package com.anxin.home.genai.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import com.anxin.home.genai.AIDataProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

    private final PlatformAccountRepository accountRepository;
    private final HousekeepingSecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final AIDataProvider dataProvider;

    public RegistrationController(PlatformAccountRepository accountRepository,
                                  HousekeepingSecurityProperties securityProperties,
                                  PasswordEncoder passwordEncoder,
                                  AIDataProvider dataProvider) {
        this.accountRepository = accountRepository;
        this.securityProperties = securityProperties;
        this.passwordEncoder = passwordEncoder;
        this.dataProvider = dataProvider;
    }

    @GetMapping("/registration-options")
    List<AIDataProvider.ServiceSkillView> registrationOptions() {
        return dataProvider.getServiceSkills();
    }

    @PostMapping("/register/customer")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse registerCustomer(@Valid @RequestBody CustomerRegistration request) {
        assertUsernameAvailable(request.username());
        Integer customerId = null;
        try {
            AIDataProvider.ProfileCreated customer = dataProvider.createCustomer(new AIDataProvider.CreateCustomerRequest(
                request.name(), request.telephone(), request.email() == null ? "" : request.email()));
            customerId = customer.id();
            dataProvider.createCustomerAddress(customerId, new AIDataProvider.CreateAddressRequest(
                request.name(), request.telephone(), request.city(), request.district(), request.detailAddress(),
                request.accessInstruction() == null ? "" : request.accessInstruction(), true));
            saveAccount(request.username(), request.password(), customerId.toString(), "CUSTOMER");
            return new RegistrationResponse(request.username(), customerId.toString(), "CUSTOMER");
        } catch (RuntimeException exception) {
            if (customerId != null) compensateCustomer(customerId, exception);
            throw exception;
        }
    }

    @PostMapping("/register/worker")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationResponse registerWorker(@Valid @RequestBody WorkerRegistration request) {
        assertUsernameAvailable(request.username());
        Integer workerId = null;
        try {
            AIDataProvider.ProfileCreated worker = dataProvider.createHousekeeper(new AIDataProvider.CreateHousekeeperRequest(
                request.name(), request.telephone(), request.introduction() == null ? "" : request.introduction(),
                request.experienceYears(), request.serviceDistrict(), request.skillIds()));
            workerId = worker.id();
            saveAccount(request.username(), request.password(), workerId.toString(), "WORKER");
            return new RegistrationResponse(request.username(), workerId.toString(), "WORKER");
        } catch (RuntimeException exception) {
            if (workerId != null) compensateWorker(workerId, exception);
            throw exception;
        }
    }

    private void assertUsernameAvailable(String username) {
        if (securityProperties.getUsers().containsKey(username) || accountRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }
    }

    private void saveAccount(String username, String password, String userId, String role) {
        accountRepository.save(new PlatformAccount(username, passwordEncoder.encode(password), userId, role));
    }

    private void compensateCustomer(int customerId, RuntimeException original) {
        try { dataProvider.deleteCustomer(customerId); }
        catch (RuntimeException compensationFailure) { original.addSuppressed(compensationFailure); }
    }

    private void compensateWorker(int workerId, RuntimeException original) {
        try { dataProvider.deleteHousekeeper(workerId); }
        catch (RuntimeException compensationFailure) { original.addSuppressed(compensationFailure); }
    }

    @ExceptionHandler({UsernameAlreadyExistsException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> duplicateUsername() {
        return Map.of("message", "该账号已被注册");
    }

    record RegistrationResponse(String username, String userId, String role) {
    }

    record CustomerRegistration(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,30}$") String username,
        @NotBlank @Size(min = 10, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 30) String telephone,
        @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 50) String city,
        @NotBlank @Size(max = 50) String district,
        @NotBlank @Size(max = 200) String detailAddress,
        @Size(max = 500) String accessInstruction
    ) {
    }

    record WorkerRegistration(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{4,30}$") String username,
        @NotBlank @Size(min = 10, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 30) String telephone,
        @Size(max = 500) String introduction,
        @Min(0) @Max(60) int experienceYears,
        @NotBlank @Size(max = 100) String serviceDistrict,
        @NotEmpty List<Integer> skillIds
    ) {
    }

    static class UsernameAlreadyExistsException extends RuntimeException {
    }
}
