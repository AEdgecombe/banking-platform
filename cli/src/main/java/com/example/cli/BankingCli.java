package com.example.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Scanner;
import java.util.UUID;

/**
 * Presentation tier. Reads input, calls the API, prints the result. No banking rules here:
 * if the API says no, the CLI prints why.
 */
public class BankingCli {

    private final ApiClient api;
    private final Scanner in;
    private final PrintStream out;

    public BankingCli(ApiClient api, InputStream in, PrintStream out) {
        this.api = api;
        this.in = new Scanner(in);
        this.out = out;
    }

    public static void main(String[] args) {
        var baseUrl = System.getenv().getOrDefault("BANKING_API_URL", "http://localhost:8080");
        new BankingCli(new ApiClient(URI.create(baseUrl)), System.in, System.out).run();
    }

    public void run() {
        while (true) {
            printMenu();
            var choice = prompt("> ");
            if (choice == null || choice.equals("0")) {
                return;
            }
            try {
                handle(choice);
            } catch (ApiException e) {
                out.println("Error [" + e.code() + "]: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                out.println("Invalid input: " + e.getMessage());
            }
        }
    }

    private void handle(String choice) {
        switch (choice) {
            case "1" -> printAccount(api.openAccount(prompt("Owner name: ")));
            case "2" -> api.listAccounts().forEach(this::printAccount);
            case "3" -> printAccount(api.getAccount(readId("Account ID: ")));
            case "4" -> printAccount(api.deposit(readId("Account ID: "), readAmount()));
            case "5" -> printAccount(api.withdraw(readId("Account ID: "), readAmount()));
            case "6" -> {
                var result = api.transfer(readId("From account ID: "), readId("To account ID: "), readAmount());
                out.println("Transfer " + result.transferId());
                printAccount(result.from());
                printAccount(result.to());
            }
            case "7" -> api.history(readId("Account ID: ")).forEach(tx -> out.printf(
                    "%s  %-12s %10s  balance %10s%n", tx.createdAt(), tx.type(), tx.amount(), tx.balanceAfter()));
            default -> out.println("Unknown option");
        }
    }

    private void printMenu() {
        out.println();
        out.println("1 Open account   2 List accounts   3 Show account");
        out.println("4 Deposit        5 Withdraw        6 Transfer");
        out.println("7 History        0 Quit");
    }

    private void printAccount(ApiClient.AccountView a) {
        out.printf("%s  %-20s %10s%n", a.id(), a.ownerName(), a.balance());
    }

    private UUID readId(String label) {
        return UUID.fromString(prompt(label));
    }

    /** Parsing only. Whether the amount is allowed is the API's decision. */
    private BigDecimal readAmount() {
        try {
            return new BigDecimal(prompt("Amount: "));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number");
        }
    }

    private String prompt(String label) {
        out.print(label);
        out.flush();
        return in.hasNextLine() ? in.nextLine().strip() : null;
    }
}
