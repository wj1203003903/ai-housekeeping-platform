package com.anxin.home.genai;

import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import com.anxin.home.genai.security.InternalServiceTokenProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@Service
public class AIDataProvider {

    private final RestClient restClient;
    private final DiscoveryClient discoveryClient;
    private final InternalServiceTokenProvider tokenProvider;
    private final Map<String, AtomicInteger> serviceIndexes = new ConcurrentHashMap<>();
    private final CircuitBreaker dependencyCircuitBreaker = CircuitBreaker.ofDefaults("business-services");
    private final Retry readRetry = Retry.of("business-service-reads", RetryConfig.custom()
        .maxAttempts(3).waitDuration(Duration.ofMillis(150)).build());

    public AIDataProvider(DiscoveryClient discoveryClient, InternalServiceTokenProvider tokenProvider) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(6));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.discoveryClient = discoveryClient;
        this.tokenProvider = tokenProvider;
    }

    public List<Map<String, Object>> getCustomers() {
        return getList(getCustomerServiceUri() + "/customers");
    }

    public List<Map<String, Object>> getCustomerAddresses(int customerId) {
        return getList(getCustomerServiceUri() + "/customers/" + customerId + "/addresses");
    }

    public ProfileCreated createCustomer(CreateCustomerRequest request) {
        return postTyped(getCustomerServiceUri() + "/customers", request, ProfileCreated.class);
    }

    public ProfileCreated createCustomerAddress(int customerId, CreateAddressRequest request) {
        return postTyped(getCustomerServiceUri() + "/customers/" + customerId + "/addresses", request, ProfileCreated.class);
    }

    public void deleteCustomer(int customerId) {
        delete(getCustomerServiceUri() + "/customers/" + customerId);
    }

    public List<Map<String, Object>> getServiceItems() {
        return getList(getVetServiceUri() + "/service-items");
    }

    public List<Map<String, Object>> getHousekeepers() {
        return getList(getVetServiceUri() + "/housekeepers");
    }

    public ProfileCreated createHousekeeper(CreateHousekeeperRequest request) {
        return postTyped(getVetServiceUri() + "/housekeepers", request, ProfileCreated.class);
    }

    public void deleteHousekeeper(int housekeeperId) {
        delete(getVetServiceUri() + "/housekeepers/" + housekeeperId);
    }

    public List<ServiceSkillView> getServiceSkills() {
        ServiceSkillView[] result = dependencyCircuitBreaker.executeSupplier(() -> restClient.get()
            .uri(getVetServiceUri() + "/service-skills")
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().body(ServiceSkillView[].class));
        return result == null ? List.of() : List.of(result);
    }

    public List<Map<String, Object>> findAvailableHousekeepers(int serviceItemId, LocalDate date,
                                                               LocalTime startTime, String district) {
        URI uri = UriComponentsBuilder.fromUri(getVetServiceUri())
            .path("/housekeepers/available")
            .queryParam("serviceItemId", serviceItemId)
            .queryParam("date", date)
            .queryParam("startTime", startTime)
            .queryParam("district", district == null ? "" : district)
            .build().encode().toUri();
        return getList(uri.toString());
    }

    public Map<String, Object> calculatePrice(int serviceItemId, BigDecimal quantity) {
        Map<String, Object> item = getServiceItems().stream()
            .filter(candidate -> Integer.parseInt(candidate.get("id").toString()) == serviceItemId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown service item " + serviceItemId));
        BigDecimal unitPrice = new BigDecimal(item.get("unitPrice").toString());
        BigDecimal minimumUnits = new BigDecimal(item.get("minimumUnits").toString());
        BigDecimal billableQuantity = quantity.max(minimumUnits);
        return Map.of(
            "serviceItemId", serviceItemId,
            "serviceName", item.get("name"),
            "quantity", billableQuantity,
            "unitPrice", unitPrice,
            "totalPrice", unitPrice.multiply(billableQuantity)
        );
    }

    public Map<String, Object> createBooking(Map<String, Object> request, String idempotencyKey) {
        return post(getVisitServiceUri() + "/bookings", request, idempotencyKey);
    }

    public List<Map<String, Object>> getCustomerBookings(int customerId) {
        return getList(getVisitServiceUri() + "/bookings?customerId=" + customerId);
    }

    public Map<String, Object> getBooking(long bookingId) {
        return getMap(getVisitServiceUri() + "/bookings/" + bookingId);
    }

    public Map<String, Object> cancelBooking(long bookingId) {
        return post(getVisitServiceUri() + "/bookings/" + bookingId + "/cancel", Map.of(), null);
    }

    private List<Map<String, Object>> getList(String uri) {
        Supplier<List<Map<String, Object>>> call = () -> {
            List<Map<String, Object>> result = restClient.get().uri(uri)
                    .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken())).retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result == null ? List.of() : result;
        };
        return Retry.decorateSupplier(readRetry,
            CircuitBreaker.decorateSupplier(dependencyCircuitBreaker, call)).get();
    }

    private Map<String, Object> getMap(String uri) {
        Supplier<Map<String, Object>> call = () -> {
            Map<String, Object> result = restClient.get().uri(uri)
                .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken())).retrieve()
                .body(new ParameterizedTypeReference<>() {});
            return result == null ? Map.of() : result;
        };
        return Retry.decorateSupplier(readRetry,
            CircuitBreaker.decorateSupplier(dependencyCircuitBreaker, call)).get();
    }

    private Map<String, Object> post(String uri, Object request, String idempotencyKey) {
        RestClient.RequestBodySpec spec = restClient.post().uri(uri);
        spec.headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()));
        if (idempotencyKey != null) spec.header("Idempotency-Key", idempotencyKey);
        Map<String, Object> result = dependencyCircuitBreaker.executeSupplier(() -> spec.body(request).retrieve()
            .body(new ParameterizedTypeReference<>() {}));
        return result == null ? Map.of() : result;
    }

    private <T> T postTyped(String uri, Object request, Class<T> responseType) {
        return dependencyCircuitBreaker.executeSupplier(() -> restClient.post().uri(uri)
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .body(request).retrieve().body(responseType));
    }

    private void delete(String uri) {
        dependencyCircuitBreaker.executeRunnable(() -> restClient.delete().uri(uri)
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().toBodilessEntity());
    }

    @NotNull
    private URI getCustomerServiceUri() {
        return serviceUri("customers-service");
    }

    @NotNull
    private URI getVetServiceUri() {
        return serviceUri("vets-service");
    }

    @NotNull
    private URI getVisitServiceUri() {
        return serviceUri("visits-service");
    }

    private URI serviceUri(String serviceId) {
        var instances = discoveryClient.getInstances(serviceId);
        if (instances.isEmpty()) throw new IllegalStateException(serviceId + " is not available");
        int index = Math.floorMod(serviceIndexes.computeIfAbsent(serviceId, ignored -> new AtomicInteger())
            .getAndIncrement(), instances.size());
        return instances.get(index).getUri();
    }

    public record ProfileCreated(int id) {}
    public record ServiceSkillView(int id, String name) {}
    public record CreateCustomerRequest(String name, String telephone, String email) {}
    public record CreateAddressRequest(String contactName, String telephone, String city, String district,
                                       String detailAddress, String accessInstruction, boolean defaultAddress) {}
    public record CreateHousekeeperRequest(String name, String telephone, String introduction, int experienceYears,
                                           String serviceDistrict, List<Integer> skillIds) {}
}
