package com.anxin.home.customers.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.anxin.home.customers.model.Customer;
import com.anxin.home.customers.model.CustomerRepository;
import com.anxin.home.customers.model.ServiceAddress;
import com.anxin.home.customers.model.ServiceAddressRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

@RestController
@RequestMapping("/customers")
class CustomerResource {

    private final CustomerRepository customerRepository;
    private final ServiceAddressRepository addressRepository;

    CustomerResource(CustomerRepository customerRepository, ServiceAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping
    List<Customer> findAll(Authentication authentication) {
        requireRole(authentication, "INTERNAL");
        return customerRepository.findAll();
    }

    @GetMapping("/{customerId}")
    Customer findOne(@PathVariable int customerId, Authentication authentication) {
        requireOwnerOrRole(authentication, customerId, "ADMIN", "INTERNAL");
        return load(customerId);
    }

    private Customer load(int customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer " + customerId + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Customer create(@Valid @RequestBody CustomerRequest request, Authentication authentication) {
        requireRole(authentication, "ADMIN", "INTERNAL");
        Customer customer = new Customer();
        apply(customer, request);
        return customerRepository.save(customer);
    }

    @PutMapping("/{customerId}")
    Customer update(@PathVariable int customerId, @Valid @RequestBody CustomerRequest request,
                    Authentication authentication) {
        requireOwnerOrRole(authentication, customerId, "ADMIN", "INTERNAL");
        Customer customer = load(customerId);
        apply(customer, request);
        return customerRepository.save(customer);
    }

    @GetMapping("/{customerId}/addresses")
    List<ServiceAddress> addresses(@PathVariable int customerId, Authentication authentication) {
        requireOwnerOrRole(authentication, customerId, "ADMIN", "INTERNAL");
        load(customerId);
        return addressRepository.findByCustomerId(customerId);
    }

    @PostMapping("/{customerId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceAddress addAddress(@PathVariable int customerId, @Valid @RequestBody AddressRequest request,
                              Authentication authentication) {
        requireOwnerOrRole(authentication, customerId, "ADMIN", "INTERNAL");
        Customer customer = load(customerId);
        ServiceAddress address = new ServiceAddress();
        address.setCustomer(customer);
        apply(address, request);
        return addressRepository.save(address);
    }

    @PutMapping("/{customerId}/addresses/{addressId}")
    ServiceAddress updateAddress(@PathVariable int customerId, @PathVariable int addressId,
                                 @Valid @RequestBody AddressRequest request, Authentication authentication) {
        requireOwnerOrRole(authentication, customerId, "ADMIN", "INTERNAL");
        ServiceAddress address = addressRepository.findById(addressId)
            .filter(item -> item.getCustomer().getId() == customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Address " + addressId + " not found"));
        apply(address, request);
        return addressRepository.save(address);
    }

    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@PathVariable int customerId, Authentication authentication) {
        requireRole(authentication, "ADMIN", "INTERNAL");
        load(customerId);
        addressRepository.deleteByCustomerId(customerId);
        customerRepository.deleteById(customerId);
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name());
        customer.setTelephone(request.telephone());
        customer.setEmail(request.email());
    }

    private void apply(ServiceAddress address, AddressRequest request) {
        address.setContactName(request.contactName());
        address.setTelephone(request.telephone());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetailAddress(request.detailAddress());
        address.setAccessInstruction(request.accessInstruction());
        address.setDefaultAddress(request.defaultAddress());
    }

    private void requireOwnerOrRole(Authentication authentication, int ownerId, String... roles) {
        if (hasAnyRole(authentication, roles)) return;
        if (authentication == null || !Integer.toString(ownerId).equals(authentication.getName())) {
            throw new AccessDeniedException("Customer data does not belong to the current account");
        }
    }

    private void requireRole(Authentication authentication, String... roles) {
        if (!hasAnyRole(authentication, roles)) throw new AccessDeniedException("Insufficient permission");
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null) return false;
        for (String role : roles) {
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role))) {
                return true;
            }
        }
        return false;
    }
}

record CustomerRequest(
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String name,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String telephone,
    @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 100) String email
) {
}

record AddressRequest(
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String contactName,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String telephone,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String city,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String district,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String detailAddress,
    @jakarta.validation.constraints.Size(max = 500) String accessInstruction,
    boolean defaultAddress
) {
}
