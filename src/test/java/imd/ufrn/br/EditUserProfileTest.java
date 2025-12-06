package imd.ufrn.br;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EditUserProfileTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private final String BASE_URL = "https://toronto.imd.ufrn.br/gestao";
    private final String LOGIN_URL = BASE_URL + "/login/";

    @Before
    public void setUp() {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        // Ensure this path matches your system
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        try {
            ensureLoggedInAndNavigateToEdit();
        } catch (Exception e) {
            // If setup fails, print why so we can debug "silent" failures
            System.err.println("SETUP FAILED: " + e.getMessage());
            throw e;
        }
    }

    @After
    public void tearDown() {
        if (driver != null) driver.quit();
        // Pause briefly to stop the server from rate-limiting us (blocking the IP)
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }

    // --- SMART SETUP LOGIC ---

    private void ensureLoggedInAndNavigateToEdit() {
        driver.get(LOGIN_URL);

        // CHECK: Are we ALREADY logged in from a previous session?
        // If we see the "User Avatar" button, we are logged in.
        boolean alreadyLoggedIn = isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário']"));

        if (!alreadyLoggedIn) {
            // Only perform login steps if we are actually logged out
            performLogin();
        }

        // Now that we are definitely logged in, go to the Edit page
        navigateToEditViaMenu();
    }

    private void performLogin() {
        Dotenv dotenv = Dotenv.load();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))
                .sendKeys(dotenv.get("TEST_USER"));

        driver.findElement(By.id("password")).sendKeys(dotenv.get("TEST_PASS"));

        // Use JS Click to avoid any overlay/focus issues
        WebElement loginBtn = driver.findElement(By.cssSelector("button[type='submit']"));
        jsClick(loginBtn);

        // Verify we actually got in
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
    }

    private void navigateToEditViaMenu() {
        // Optimization: If already on the edit page, stop here.
        if (isElementPresent(By.id("input-nome"))) return;

        // 1. Open User Menu
        WebElement userMenuBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
        jsClick(userMenuBtn);

        // 2. Click "Editar conta"
        WebElement editLink = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//a[contains(@href, 'usuario/conta/editar') or contains(., 'Editar')]")
        ));
        jsClick(editLink);

        // 3. Wait for the page to load
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("input-nome")));
    }

    // --- UTILITIES ---

    private void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    private boolean isElementPresent(By locator) {
        try {
            return driver.findElements(locator).size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void robustClear(WebElement element) {
        element.click();
        element.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);
    }

    private boolean checkForErrorToast() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-error")));
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean checkForSuccessToast() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-success")));
            return true;
        } catch (Exception e) { return false; }
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();
        return temp;
    }

    // --- TESTS ---

    @Test
    public void test01_AvatarUpdate() throws IOException, InterruptedException {
        System.out.println(">>> START: test01_AvatarUpdate");

        String oldSrc = "none";
        // Grab old src if it exists (some users might not have an avatar set yet)
        if (isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário'] img"))) {
            oldSrc = driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário'] img")).getAttribute("src");
        }

        WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("formFile")));
        File goodFile = createTempFile("valid_image", ".jpg");
        fileInput.sendKeys(goodFile.getAbsolutePath());

        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

        checkForSuccessToast();
        Thread.sleep(3000); // Give backend time to process image

        if (isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário'] img"))) {
            String newSrc = driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário'] img")).getAttribute("src");
            assertNotEquals("Failure: Avatar src did not change", oldSrc, newSrc);
        }
        System.out.println("FINISHED: test01_AvatarUpdate");
    }

    @Test
    public void test02_ValidNameUpdate() {
        System.out.println(">>> START: test02_ValidNameUpdate");
        String randomName = "User_" + UUID.randomUUID().toString().substring(0, 8);
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));

        robustClear(nameInput);
        nameInput.sendKeys(randomName);

        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        checkForSuccessToast();

        navigateToEditViaMenu(); // Reload page to verify persistence

        WebElement finalNameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        assertEquals("Name did not save", randomName.toUpperCase(), finalNameInput.getAttribute("value"));
        System.out.println("FINISHED: test02_ValidNameUpdate");
    }

    @Test
    public void test03_ImmutableLoginField() {
        System.out.println(">>> START: test03_ImmutableLoginField");
        WebElement loginLabel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lbl-input-login")));
        String containerText = loginLabel.findElement(By.xpath("./..")).getText();
        String expectedUser = Dotenv.load().get("TEST_USER").trim();

        assertTrue("Username not displayed in read-only field", containerText.contains(expectedUser));
        System.out.println("FINISHED: test03_ImmutableLoginField");
    }

    @Test
    public void test04_CancelButton() {
        System.out.println(">>> START: test04_CancelButton");
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        String originalName = nameInput.getAttribute("value");

        robustClear(nameInput);
        nameInput.sendKeys("SHOULD_NOT_SAVE");

        jsClick(driver.findElement(By.xpath("//button[contains(.,'Cancelar')]")));
        wait.until(ExpectedConditions.urlContains("listar-empresas")); // Wait for redirect

        navigateToEditViaMenu(); // Go back to check
        WebElement finalName = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));

        assertEquals("Cancel button incorrectly saved data", originalName, finalName.getAttribute("value"));
        System.out.println("FINISHED: test04_CancelButton");
    }

    @Test
    public void test05_DateOfBirthInitialLoad() {
        System.out.println(">>> START: test05_DateOfBirthInitialLoad");
        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.dataNascimento']")));
        assertFalse("DOB is empty", dateInput.getAttribute("value").isEmpty());
        System.out.println("FINISHED: test05_DateOfBirthInitialLoad");
    }

    @Test
    public void test06_EmptyNameValidation() {
        System.out.println(">>> START: test06_EmptyNameValidation");
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        robustClear(nameInput);
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        assertTrue("System accepted empty name", checkForErrorToast());
        System.out.println("FINISHED: test06_EmptyNameValidation");
    }

    @Test
    public void test07_CpfValidation() {
        System.out.println(">>> START: test07_CpfValidation");
        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.cpf']")));
        robustClear(cpfInput);
        cpfInput.sendKeys("111.111.111-11");
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        assertTrue("System accepted invalid CPF", checkForErrorToast());
        System.out.println("FINISHED: test07_CpfValidation");
    }

    @Test
    public void test08_EmailValidation() {
        System.out.println(">>> START: test08_EmailValidation");

        String[] invalidEmails = {"user@", "userdomain.com", "user@domain"};

        By localErrorLocator = By.xpath("//input[@id='input-email']/following-sibling::*[contains(text(), 'email válido')]");

        for (String email : invalidEmails) {
            WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));
            robustClear(emailInput);
            emailInput.sendKeys(email);

            // 1. Attempt Save (Triggers Client-Side Logic)
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

            // 2. Check for the local validation message (short wait)
            boolean localErrorVisible = false;
            try {
                // Wait specifically for the local validation text to appear
                new WebDriverWait(driver, Duration.ofSeconds(2)).until(
                        ExpectedConditions.visibilityOfElementLocated(localErrorLocator)
                );
                localErrorVisible = true;
            } catch (Exception e) {}

            // Assertion: We expect the local error message to be visible
            assertTrue("Client validation failed for email: " + email + ". Expected local error message.", localErrorVisible);

            // Cleanup
            navigateToEditViaMenu();
        }
        System.out.println("FINISHED: test08_EmailValidation");
    }

    @Test
    public void test09_AgeBoundaries() {
        System.out.println(">>> START: test09_AgeBoundaries");

        // A. Future Date
        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.dataNascimento']")));
        robustClear(dateInput);
        dateInput.sendKeys(LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        assertTrue("Allowed Future Date", checkForErrorToast());

        navigateToEditViaMenu();

        // B. Ancient Date
        dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.dataNascimento']")));
        robustClear(dateInput);
        dateInput.sendKeys("01/01/0024");
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

        // If system says "Success", that is a BUG in the system logic.
        boolean ancientSuccess = checkForSuccessToast();
        assertFalse("Bug: System accepted year 0024", ancientSuccess);
        System.out.println("FINISHED: test09_AgeBoundaries");
    }

    @Test
    public void test10_SecurityInvalidFileUploads() throws IOException {
        System.out.println(">>> START: test10_SecurityInvalidFileUploads");
        String[] dangerousExtensions = {".txt", ".exe", ".sh", ".html"};

        for (String ext : dangerousExtensions) {
            navigateToEditViaMenu();
            WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("formFile")));
            fileInput.sendKeys(createTempFile("exploit", ext).getAbsolutePath());
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

            assertTrue("Security Fail: Accepted " + ext, checkForErrorToast());
        }
        System.out.println("FINISHED: test10_SecurityInvalidFileUploads");
    }

    @Test
    public void test13_EmailChangeAndLogin() {
        System.out.println(">>> START: test13_EmailChangeAndLogin");
        Dotenv dotenv = Dotenv.load();

        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));
        String originalEmail = emailInput.getAttribute("value");
        String newEmail = "auto_" + UUID.randomUUID().toString().substring(0,6) + "@example.com";

        robustClear(emailInput);
        emailInput.sendKeys(newEmail);
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

        if (checkForSuccessToast()) {
            boolean loginSucceeded = false;
            try {
                // 1. Open User Menu
                WebElement userMenu = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
                jsClick(userMenu);

                // FIX: Use the correct locator for the <button> with text "Sair"
                WebElement logoutLink = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[contains(., 'Sair')]")));
                jsClick(logoutLink);

                wait.until(ExpectedConditions.urlContains("/login"));

                // 2. Explicitly Try Login with NEW Email
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(newEmail);
                driver.findElement(By.id("password")).sendKeys(dotenv.get("TEST_PASS"));
                jsClick(driver.findElement(By.cssSelector("button[type='submit']")));

                try {
                    // Check if we got in
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
                    loginSucceeded = true;
                } catch (Exception e) { loginSucceeded = false; }

                // 3. Cleanup
                if (loginSucceeded) {
                    navigateToEditViaMenu();
                    emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));
                    robustClear(emailInput);
                    emailInput.sendKeys(originalEmail);
                    jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
                    checkForSuccessToast();
                }

            } finally {
                assertFalse("Security Warning: Immediate login with new email allowed", loginSucceeded);
            }
        }
        System.out.println("FINISHED: test13_EmailChangeAndLogin");
    }
}