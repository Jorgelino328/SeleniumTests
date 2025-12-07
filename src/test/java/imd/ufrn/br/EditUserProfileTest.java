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
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EditUserProfileTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);
    private final String BASE_URL = "https://toronto.imd.ufrn.br/gestao";
    private final String LOGIN_URL = BASE_URL + "/login/";

    @Before
    public void setUp() {
        Logger.getLogger("org.openqa.selenium").setLevel(Level.SEVERE);
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);

        try {
            ensureLoggedInAndNavigateToEdit();
        } catch (Exception e) {
            System.err.println("SETUP FAILED: " + e.getMessage());
            throw e;
        }
    }

    @After
    public void tearDown() {
        if (driver != null) driver.quit();
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }

    // --- SMART NAVIGATION AND SETUP ---

    private void ensureLoggedInAndNavigateToEdit() {
        driver.get(LOGIN_URL);
        if (!isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário']"))) {
            performLogin();
        }
        navigateToEditViaMenu();
    }

    private void performLogin() {
        Dotenv dotenv = Dotenv.load();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")))
                .sendKeys(dotenv.get("TEST_USER"));
        driver.findElement(By.id("password")).sendKeys(dotenv.get("TEST_PASS"));
        WebElement loginBtn = driver.findElement(By.cssSelector("button[type='submit']"));
        jsClick(loginBtn);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
    }

    // Used for re-login inside test14
    private void performLoginWithCredentials(String user, String pass) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(user);
        driver.findElement(By.id("password")).sendKeys(pass);
        jsClick(driver.findElement(By.cssSelector("button[type='submit']")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
    }

    private void navigateToEditViaMenu() {
        if (driver.getCurrentUrl().contains("/usuario/conta/editar") && isElementPresent(By.id("input-nome"))) {
            return;
        }
        driver.get(BASE_URL + "/usuario/conta/editar");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("input-nome")));
    }

    private void navigateToLGPDPane() {
        String lgpdUrl = BASE_URL + "/lgpd";

        // Force navigation if not already there
        if (!driver.getCurrentUrl().contains("lgpd")) {
            driver.get(lgpdUrl);
        }

        // Wait for the main page title (H3) which effectively signals page load
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//h3[contains(., 'Sobre os meus dados')]")
        ));
    }

    private void navigateToPasswordChange() {
        String pwUrl = BASE_URL + "/usuario/conta/senha";
        if (!driver.getCurrentUrl().contains("senha")) {
            driver.get(pwUrl);
        }
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type='password']")));
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

    private boolean checkForLocalError(By inputLocator, String expectedErrorTextPart) {
        String locatorString = inputLocator.toString();
        String baseId;
        if (locatorString.contains("By.id: ")) {
            baseId = locatorString.substring(locatorString.indexOf(":") + 1).trim();
        } else if (locatorString.contains("By.cssSelector: input[id=")) {
            baseId = locatorString.substring(locatorString.indexOf("'")+1, locatorString.lastIndexOf("']")).trim();
        } else {
            return false;
        }
        By localErrorLocator = By.xpath("//*[@id='" + baseId + "']/following-sibling::*[contains(text(), '" + expectedErrorTextPart + "')]");
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2)).until(ExpectedConditions.visibilityOfElementLocated(localErrorLocator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkForErrorToast() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-error")));
            return true;
        } catch (Exception e) { return false; }
    }

    private boolean checkForSuccessToast() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-success")));
            return true;
        } catch (Exception e) { return false; }
    }

    private File createTempFile(String prefix, String suffix) throws IOException {
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();
        return temp;
    }

    // --- TEST SUITE ---

    @Test
    public void test01_AvatarUpdate() throws IOException, InterruptedException {
        System.out.println(">>> START: test01_AvatarUpdate");
        String oldSrc = "none";
        if (isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário'] img"))) {
            oldSrc = driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário'] img")).getAttribute("src");
        }
        WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("formFile")));
        File goodFile = createTempFile("valid_image", ".jpg");
        fileInput.sendKeys(goodFile.getAbsolutePath());
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        checkForSuccessToast();
        Thread.sleep(3000);
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
        navigateToEditViaMenu();
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
        wait.until(ExpectedConditions.urlContains("listar-empresas"));
        navigateToEditViaMenu();
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
        boolean localError = checkForLocalError(By.id("input-nome"), "campo nome é obrigatório");
        assertTrue("System accepted empty name (Did not show local 'Obrigatório' error)", localError);
        System.out.println("FINISHED: test06_EmptyNameValidation");
    }

    @Test
    public void test07_EmptyCpfValidation() {
        System.out.println(">>> START: test07_EmptyCpfValidation");
        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.cpf']")));
        robustClear(cpfInput);
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        boolean localError = checkForLocalError(By.cssSelector("input[id='pessoa.cpf']"), "campo cpf é obrigatório");
        assertTrue("System accepted empty CPF (Did not show local required error)", localError);
        System.out.println("FINISHED: test07_EmptyCpfValidation");
    }

    @Test
    public void test08_CpfValidation() {
        System.out.println(">>> START: test08_CpfValidation");
        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.cpf']")));
        robustClear(cpfInput);
        cpfInput.sendKeys("111.111.111-11");
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        boolean localError = checkForLocalError(By.cssSelector("input[id='pessoa.cpf']"), "Informe um CPF válido");
        assertTrue("System accepted invalid CPF checksum (Did not show local error)", localError);
        System.out.println("FINISHED: test08_CpfValidation");
    }

    @Test
    public void test09_EmailValidation() {
        System.out.println(">>> START: test09_EmailValidation");
        String[] invalidEmails = {"user@", "userdomain.com", "user@domain"};
        By localErrorLocatorBase = By.id("input-email");
        for (String email : invalidEmails) {
            WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(localErrorLocatorBase));
            robustClear(emailInput);
            emailInput.sendKeys(email);
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
            boolean localError = checkForLocalError(localErrorLocatorBase, "email válido");
            assertTrue("Client validation failed for email: " + email, localError);
            navigateToEditViaMenu();
        }
        System.out.println("FINISHED: test09_EmailValidation");
    }

    @Test
    public void test10_AgeBoundaries() {
        System.out.println(">>> START: test10_AgeBoundaries");
        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.dataNascimento']")));
        By dateLocator = By.cssSelector("input[id='pessoa.dataNascimento']");
        robustClear(dateInput);
        dateInput.sendKeys(LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        boolean localError = checkForLocalError(dateLocator, "Data inválida");
        assertTrue("Allowed Future Date (Did not show local error)", localError);
        navigateToEditViaMenu();
        dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(dateLocator));
        robustClear(dateInput);
        dateInput.sendKeys("01/01/0024");
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        boolean ancientSuccess = checkForSuccessToast();
        assertFalse("Bug: System allowed unrealistic ancient date (01/01/0024)", ancientSuccess);
        System.out.println("FINISHED: test10_AgeBoundaries");
    }

    @Test
    public void test11_SecurityInvalidFileUploads() throws IOException {
        System.out.println(">>> START: test11_SecurityInvalidFileUploads");
        String[] dangerousExtensions = {".txt", ".exe", ".sh", ".html"};
        for (String ext : dangerousExtensions) {
            navigateToEditViaMenu();
            WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("formFile")));
            fileInput.sendKeys(createTempFile("exploit", ext).getAbsolutePath());
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
            assertTrue("Security Fail: Accepted " + ext, checkForErrorToast());
        }
        System.out.println("FINISHED: test11_SecurityInvalidFileUploads");
    }

    @Test
    public void test12_DataPrivacyMasking() {
        System.out.println(">>> START: test12_DataPrivacyMasking");

        navigateToLGPDPane();

        // Wait for the table to exist
        WebElement lgpdTable = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".table.mt-2")));

        // 1. Extract CPF
        WebElement cpfCell = lgpdTable.findElement(By.xpath(".//td[.//strong[contains(text(), 'CPF')]]/following-sibling::td"));
        String cpfMasked = cpfCell.getText().trim();
        if (cpfMasked.isEmpty()) cpfMasked = cpfCell.getAttribute("textContent").trim(); // Fallback

        // 2. Extract Email
        WebElement emailCell = lgpdTable.findElement(By.xpath(".//td[.//strong[contains(text(), 'E-mail')]]/following-sibling::td"));
        String emailMasked = emailCell.getText().trim();
        if (emailMasked.isEmpty()) emailMasked = emailCell.getAttribute("textContent").trim(); // Fallback

        // 3. Assertions (Strict Regex)
        assertTrue("CPF masking failed. Found: " + cpfMasked,
                cpfMasked.matches("^\\*\\*\\*\\.\\d{3}\\.\\d{3}-\\*\\*$"));

        assertTrue("Email masking failed. Found: " + emailMasked,
                emailMasked.matches("^.+\\*{5}.+@.+$"));

        navigateToEditViaMenu();

        System.out.println("FINISHED: test12_DataPrivacyMasking");
    }

    @Test
    public void test13_DataPersistenceUpdate() {
        System.out.println(">>> START: test13_DataPersistenceUpdate");
        final String NEW_DOB = "10/06/1990";
        navigateToLGPDPane();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".table.mt-2")));
        navigateToEditViaMenu();
        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[id='pessoa.dataNascimento']")));
        robustClear(dateInput);
        dateInput.sendKeys(NEW_DOB);
        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
        checkForSuccessToast();
        navigateToLGPDPane();
        WebElement lgpdTableFinal = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".table.mt-2")));
        String finalDobValue = lgpdTableFinal.findElement(By.xpath(".//tr[4]/td[2]")).getText().trim();
        assertEquals("DOB failed to update/persist on the LGPD screen.", NEW_DOB, finalDobValue);
        navigateToEditViaMenu();
        System.out.println("FINISHED: test13_DataPersistenceUpdate");
    }

    @Test
    public void test14_EmailChangeAndLogin() {
        System.out.println(">>> START: test14_EmailChangeAndLogin");
        Dotenv dotenv = Dotenv.load();

        final String NEW_EMAIL = "auto_" + UUID.randomUUID().toString().substring(0,6) + "@example.com";
        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));
        String originalEmail = emailInput.getAttribute("value");

        try {
            // 1. Change Email
            robustClear(emailInput);
            emailInput.sendKeys(NEW_EMAIL);
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
            assertTrue("Could not save new email", checkForSuccessToast());

            // 2. Logout (Force navigation to logout for speed/reliability)
            WebElement userMenu = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
            jsClick(userMenu);
            WebElement logoutLink = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//button[contains(., 'Sair')]")));
            jsClick(logoutLink);
            wait.until(ExpectedConditions.urlContains("/login"));

            // 3. Login with NEW Email
            performLoginWithCredentials(NEW_EMAIL, dotenv.get("TEST_PASS"));

            // 4. Verify we are logged in
            assertTrue("Login with new email failed", isElementPresent(By.cssSelector("button[aria-label='Abrir menu do usuário']")));

        } finally {
            // 5. Cleanup: Revert to original email
            navigateToEditViaMenu();
            WebElement cleanupEmailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));
            robustClear(cleanupEmailInput);
            cleanupEmailInput.sendKeys(originalEmail);
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));
            checkForSuccessToast();
        }
        System.out.println("FINISHED: test14_EmailChangeAndLogin");
    }

    @Test
    public void test15_PasswordMismatchValidation() {
        System.out.println(">>> START: test15_PasswordMismatchValidation");
        navigateToPasswordChange();

        List<WebElement> passwordFields = driver.findElements(By.cssSelector("input[type='password']"));

        if (passwordFields.size() >= 3) {
            passwordFields.get(1).sendKeys("NewPass123!");
            passwordFields.get(2).sendKeys("MismatchPass999!");
            jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

            // Check for general error
            boolean hasError = false;
            try {
                // Look for common mismatch text in page
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(text(), 'conferem') or contains(text(), 'coincidem') or contains(text(), 'iguais')]")));
                hasError = true;
            } catch (Exception e) {
                hasError = checkForErrorToast();
            }
            assertTrue("System accepted mismatched passwords or showed no error.", hasError);
        }
        System.out.println("FINISHED: test15_PasswordMismatchValidation");
    }
}