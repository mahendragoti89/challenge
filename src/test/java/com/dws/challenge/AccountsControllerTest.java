package com.dws.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.math.BigDecimal;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
class AccountsControllerTest {

  private MockMvc mockMvc;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @BeforeEach
  void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
      .andExpect(status().isOk())
      .andExpect(
        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }

  @Test
  void transferMoney_ValidTransfer_Success() throws Exception {
    // Given - Create accounts
    Account account1 = new Account("ACC001", new BigDecimal("1000"));
    Account account2 = new Account("ACC002", new BigDecimal("500"));

    this.accountsService.createAccount(account1);
    this.accountsService.createAccount(account2);

    // When - Perform transfer
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\",\"amount\":200}"))
            .andExpect(status().isOk())
            .andExpect(content().string("Transfer completed successfully"));

    // Then - Verify balances
    assertThat(this.accountsService.getAccount("ACC001").getBalance()).isEqualTo(new BigDecimal("800"));
    assertThat(this.accountsService.getAccount("ACC002").getBalance()).isEqualTo(new BigDecimal("700"));

  }

  @Test
  void transferMoney_InsufficientFunds_BadRequest() throws Exception {
    // Given - Create accounts with insufficient funds
    Account account1 = new Account("ACC001", new BigDecimal("50"));
    Account account2 = new Account("ACC002", new BigDecimal("500"));

    this.accountsService.createAccount(account1);
    this.accountsService.createAccount(account2);

    // When & Then
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Insufficient funds")));

    // Verify balances are unchanged
    assertThat(this.accountsService.getAccount("ACC001").getBalance()).isEqualTo(new BigDecimal("50"));
    assertThat(this.accountsService.getAccount("ACC002").getBalance()).isEqualTo(new BigDecimal("500"));
  }

  @Test
  void transferMoney_AccountNotFound_BadRequest() throws Exception {
    // Given - Create only one account
    Account account1 = new Account("ACC001", new BigDecimal("1000"));
    this.accountsService.createAccount(account1);

    // When & Then
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC999\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Account not found: ACC999"));
  }

  @Test
  void transferMoney_SameAccount_BadRequest() throws Exception {
    // Given - Create account
    Account account1 = new Account("ACC001", new BigDecimal("1000"));
    this.accountsService.createAccount(account1);

    // When & Then
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC001\",\"amount\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Cannot transfer money to the same account"));
  }

  @Test
  void transferMoney_InvalidAmount_BadRequest() throws Exception {
    // Given - Create accounts
    Account account1 = new Account("ACC001", new BigDecimal("1000"));
    Account account2 = new Account("ACC002", new BigDecimal("500"));

    this.accountsService.createAccount(account1);
    this.accountsService.createAccount(account2);

    // Test negative amount
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\",\"amount\":-100}"))
            .andExpect(status().isBadRequest());

    // Test zero amount
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\",\"amount\":0}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void transferMoney_MissingFields_BadRequest() throws Exception {
    // Test missing accountFromId
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountToId\":\"ACC002\",\"amount\":100}"))
            .andExpect(status().isBadRequest());

    // Test missing accountToId
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"amount\":100}"))
            .andExpect(status().isBadRequest());

    // Test missing amount
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\"}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void transferMoney_MultipleTransfers_Success() throws Exception {
    // Given - Create accounts
    Account account1 = new Account("ACC001", new BigDecimal("1000"));
    Account account2 = new Account("ACC002", new BigDecimal("500"));
    Account account3 = new Account("ACC003", new BigDecimal("200"));

    this.accountsService.createAccount(account1);
    this.accountsService.createAccount(account2);
    this.accountsService.createAccount(account3);

    // When - Perform multiple transfers
    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC001\",\"accountToId\":\"ACC002\",\"amount\":100}"))
            .andExpect(status().isOk());

    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC002\",\"accountToId\":\"ACC003\",\"amount\":50}"))
            .andExpect(status().isOk());

    this.mockMvc.perform(post("/v1/accounts/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"accountFromId\":\"ACC003\",\"accountToId\":\"ACC001\",\"amount\":25}"))
            .andExpect(status().isOk());

    // Then - Verify final balances
    assertThat(this.accountsService.getAccount("ACC001").getBalance()).isEqualTo(new BigDecimal("925")); // 1000 - 100 + 25
    assertThat(this.accountsService.getAccount("ACC002").getBalance()).isEqualTo(new BigDecimal("550")); // 500 + 100 - 50
    assertThat(this.accountsService.getAccount("ACC003").getBalance()).isEqualTo(new BigDecimal("225")); // 200 + 50 - 25

    // Verify total balance is conserved
    BigDecimal totalBalance = this.accountsService.getAccount("ACC001").getBalance()
            .add(this.accountsService.getAccount("ACC002").getBalance())
            .add(this.accountsService.getAccount("ACC003").getBalance());
    assertThat(totalBalance).isEqualTo(new BigDecimal("1700")); // 1000 + 500 + 200
  }
}
