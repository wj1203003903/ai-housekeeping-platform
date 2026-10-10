package com.anxin.home.visits.web;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import com.anxin.home.visits.security.InternalServiceTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
class BookingReferenceClient {
    private final DiscoveryClient discoveryClient;
    private final InternalServiceTokenProvider tokenProvider;
    private final RestClient restClient;

    BookingReferenceClient(DiscoveryClient discoveryClient, InternalServiceTokenProvider tokenProvider) {
        this.discoveryClient = discoveryClient;
        this.tokenProvider = tokenProvider;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    AddressSnapshot requireAddress(int customerId, int addressId) {
        requireCustomer(customerId);
        List<AddressSnapshot> addresses = restClient.get()
            .uri(serviceUri("customers-service") + "/customers/" + customerId + "/addresses")
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().body(new ParameterizedTypeReference<>() {});
        return (addresses == null ? List.<AddressSnapshot>of() : addresses).stream()
            .filter(address -> address.id() == addressId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("服务地址不属于当前客户"));
    }

    ServiceItemSnapshot requireServiceItem(int serviceItemId) {
        return restClient.get().uri(serviceUri("vets-service") + "/service-items/" + serviceItemId)
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().body(ServiceItemSnapshot.class);
    }

    void reserve(int housekeeperId, ReservationCommand command) {
        restClient.post().uri(serviceUri("vets-service") + "/housekeepers/" + housekeeperId + "/reservations")
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .body(command).retrieve().toBodilessEntity();
    }

    void release(String reservationKey) {
        if (reservationKey == null) return;
        restClient.post().uri(serviceUri("vets-service") + "/housekeepers/reservations/" + reservationKey + "/release")
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().toBodilessEntity();
    }

    private void requireCustomer(int customerId) {
        restClient.get().uri(serviceUri("customers-service") + "/customers/" + customerId)
            .headers(headers -> headers.setBearerAuth(tokenProvider.bearerToken()))
            .retrieve().toBodilessEntity();
    }

    private URI serviceUri(String serviceId) {
        return discoveryClient.getInstances(serviceId).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException(serviceId + " is unavailable")).getUri();
    }

    record AddressSnapshot(int id, String district) {}
    record ServiceItemSnapshot(int id, String name, BigDecimal unitPrice, BigDecimal minimumUnits,
                               int estimatedMinutes, boolean enabled) {}
    record ReservationCommand(int serviceItemId, LocalDate serviceDate, LocalTime startTime, LocalTime endTime,
                              String district, String reservationKey) {}
}
