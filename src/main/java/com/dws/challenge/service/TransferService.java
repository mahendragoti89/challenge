package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.dto.TransferRequest;
import com.dws.challenge.exception.AccountNotFoundException;
import com.dws.challenge.exception.InsufficientFundsException;
import com.dws.challenge.exception.InvalidTransferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TransferService {

    private final AccountsService accountsService;
    private final NotificationService notificationService;

    // Lock per account to ensure thread-safe operations and prevent deadlocks
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    @Autowired
    public TransferService(AccountsService accountsService, NotificationService notificationService) {
        this.accountsService = accountsService;
        this.notificationService = notificationService;
    }

    public void transferMoney(TransferRequest transferRequest) {
        String fromAccountId = transferRequest.getAccountFromId();
        String toAccountId = transferRequest.getAccountToId();
        BigDecimal amount = transferRequest.getAmount();

        log.info("Starting transfer of {} from account {} to account {}", amount, fromAccountId, toAccountId);

        validateTransferRequest(transferRequest);

        // Get accounts (will throw exception if not found)
        Account fromAccount = getAccountSafely(fromAccountId);
        Account toAccount = getAccountSafely(toAccountId);

        // Perform transfer with proper locking to prevent deadlocks
        performTransfer(fromAccount, toAccount, amount);

        // Send notifications to both account holders
        sendNotifications(fromAccount, toAccount, amount);

        log.info("Successfully transferred {} from account {} to account {}", amount, fromAccountId, toAccountId);
    }

    private void validateTransferRequest(TransferRequest transferRequest) {
        if (transferRequest.getAccountFromId().equals(transferRequest.getAccountToId())) {
            throw new InvalidTransferException("Cannot transfer money to the same account");
        }

        if (transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }
    }

    private Account getAccountSafely(String accountId) {
        Account account = accountsService.getAccount(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        return account;
    }

    private void performTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {

        String firstLockId = fromAccount.getAccountId().compareTo(toAccount.getAccountId()) < 0
                ? fromAccount.getAccountId() : toAccount.getAccountId();
        String secondLockId = fromAccount.getAccountId().compareTo(toAccount.getAccountId()) < 0
                ? toAccount.getAccountId() : fromAccount.getAccountId();

        Lock firstLock = getAccountLock(firstLockId);
        Lock secondLock = getAccountLock(secondLockId);

        firstLock.lock();
        try {
            secondLock.lock();
            try {
                // Check if fromAccount has sufficient funds
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException(
                            "Insufficient funds in account " + fromAccount.getAccountId() +
                                    ". Available: " + fromAccount.getBalance() + ", Required: " + amount
                    );
                }

                // Perform the actual transfer
                fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
                toAccount.setBalance(toAccount.getBalance().add(amount));

                log.debug("Transfer completed. From account {} new balance: {}, To account {} new balance: {}",
                        fromAccount.getAccountId(), fromAccount.getBalance(),
                        toAccount.getAccountId(), toAccount.getBalance());

            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    private Lock getAccountLock(String accountId) {
        return accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
    }

    private void sendNotifications(Account fromAccount, Account toAccount, BigDecimal amount) {
        try {
            // Notify sender
            String senderMessage = String.format("Transferred %s to account %s", amount, toAccount.getAccountId());
            notificationService.notifyAboutTransfer(fromAccount, senderMessage);

            // Notify receiver
            String receiverMessage = String.format("Received %s from account %s", amount, fromAccount.getAccountId());
            notificationService.notifyAboutTransfer(toAccount, receiverMessage);
        } catch (Exception e) {
            log.error("Failed to send notifications for transfer", e);
        }
    }
}