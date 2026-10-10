package com.anxin.home.genai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.anxin.home.genai.AIDataProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class RegistrationControllerTest {

    private PlatformAccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private AIDataProvider dataProvider;
    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        accountRepository = mock(PlatformAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        dataProvider = mock(AIDataProvider.class);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}encoded");
        controller = new RegistrationController(accountRepository, new HousekeepingSecurityProperties(),
            passwordEncoder, dataProvider);
    }

    @Test
    void registersCustomerProfileAddressAndLoginAccount() {
        when(dataProvider.createCustomer(any())).thenReturn(new AIDataProvider.ProfileCreated(12));
        when(dataProvider.createCustomerAddress(any(Integer.class), any())).thenReturn(new AIDataProvider.ProfileCreated(31));

        var response = controller.registerCustomer(new RegistrationController.CustomerRegistration(
            "zhang_user", "password123", "张女士", "13800000000", "zhang@example.com",
            "上海市", "浦东新区", "世纪大道 1 号", "请提前联系"));

        assertThat(response.userId()).isEqualTo("12");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        verify(dataProvider).createCustomerAddress(12, new AIDataProvider.CreateAddressRequest(
            "张女士", "13800000000", "上海市", "浦东新区", "世纪大道 1 号", "请提前联系", true));
        ArgumentCaptor<PlatformAccount> account = ArgumentCaptor.forClass(PlatformAccount.class);
        verify(accountRepository).save(account.capture());
        assertThat(account.getValue().getUsername()).isEqualTo("zhang_user");
        assertThat(account.getValue().getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void registersWorkerProfileSkillsAndLoginAccount() {
        when(dataProvider.createHousekeeper(any())).thenReturn(new AIDataProvider.ProfileCreated(9));

        var response = controller.registerWorker(new RegistrationController.WorkerRegistration(
            "worker_li", "password123", "李师傅", "13900000000", "十年保洁经验",
            10, "全市", List.of(1, 2)));

        assertThat(response.userId()).isEqualTo("9");
        assertThat(response.role()).isEqualTo("WORKER");
        verify(dataProvider).createHousekeeper(new AIDataProvider.CreateHousekeeperRequest(
            "李师傅", "13900000000", "十年保洁经验", 10, "全市", List.of(1, 2)));
        ArgumentCaptor<PlatformAccount> account = ArgumentCaptor.forClass(PlatformAccount.class);
        verify(accountRepository).save(account.capture());
        assertThat(account.getValue().getUsername()).isEqualTo("worker_li");
        assertThat(account.getValue().getRole()).isEqualTo("WORKER");
    }

    @Test
    void compensatesCustomerProfileWhenAccountPersistenceFails() {
        when(dataProvider.createCustomer(any())).thenReturn(new AIDataProvider.ProfileCreated(12));
        when(dataProvider.createCustomerAddress(any(Integer.class), any())).thenReturn(new AIDataProvider.ProfileCreated(31));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
            .when(accountRepository).save(any(PlatformAccount.class));

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> controller.registerCustomer(new RegistrationController.CustomerRegistration(
                "zhang_user", "password123", "张女士", "13800000000", "zhang@example.com",
                "上海市", "浦东新区", "世纪大道 1 号", "请提前联系")));

        verify(dataProvider).deleteCustomer(12);
    }
}
