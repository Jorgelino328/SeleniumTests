package imd.ufrn.br;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EditUserProfileTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private final String LOGIN_URL = "https://toronto.imd.ufrn.br/gestao/login/";

    @Before
    public void setUp() {
        // 0. Set the command timeout
        System.setProperty("webdriver.http.timeout", "30000");

        // 1. Explicitly set the ChromeDriver path
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // 2. Configure Chrome Options for Headless Linux Execution
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // 3. Initialize the driver
        driver = new ChromeDriver(options);

        // Set large window size to prevent menu collapsing
        driver.manage().window().setSize(new Dimension(1920, 1080));
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // --- Helper Methods ---

    private void clickElement(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private void closeToastNotification() {
        // Try to close toast if it appears
        try {
            clickElement(By.cssSelector(".p-toast-icon-close-icon, .p-toast-icon-close"));
        } catch (Exception e) {
            // Ignore if no toast to close
        }
    }

    // Helper to force click elements that might be "intercepted" by toasts
    private void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    // --- Test Method ---

    @Test
    public void editUserProfile() {
        // --- LOGIN SECTION ---
        driver.get(LOGIN_URL);

        // TODO: PASTE YOUR REAL CREDENTIALS HERE
        String myUsername = "YOUR_REAL_USERNAME";
        String myPassword = "YOUR_REAL_PASSWORD";

        // IDs found on the specific login page
        String usernameFieldId = "username";
        String passwordFieldId = "password";

        // Perform Login
        WebElement userField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(usernameFieldId)));
        userField.clear();
        userField.sendKeys(myUsername);

        WebElement passField = driver.findElement(By.id(passwordFieldId));
        passField.sendKeys(myPassword);
        passField.submit(); // Submit form

        // Wait for Redirect and User Menu Presence
        wait.until(ExpectedConditions.urlContains("listar-empresas"));
        System.out.println("Login Successful. URL: " + driver.getCurrentUrl());

        // Wait for the specific menu button (by aria-label)
        WebElement menuButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));

        // GENERATE RANDOM NAME (Fixes the "TESTEEDIT123TESTEEDIT123" error)
        String randomName = "User_" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("Testing with new name: " + randomName);

        // --- SECTION 1: POSITIVE TEST (Valid Name Update) ---

        // Open Menu & Click Edit
        // We use JS click here just in case animations are still running
        jsClick(menuButton);
        clickElement(By.linkText("Editar"));

        // Update Name with Robust Clear
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        nameInput.click();
        // Robust clear: Select All + Backspace (better than .clear())
        nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        nameInput.sendKeys(Keys.BACK_SPACE);
        nameInput.sendKeys(randomName);

        // Save (Find button by text 'Salvar' to be safe)
        WebElement saveButton = driver.findElement(By.xpath("//button[contains(.,'Salvar')]"));
        jsClick(saveButton); // Force click

        // WAITING FOR TOAST TO DISAPPEAR
        // This is critical to prevent "element click intercepted" errors in the next step
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            shortWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".p-toast-message-text")));
        } catch (Exception e) {
            System.out.println("Toast did not disappear strictly, proceeding anyway...");
        }

        // --- SECTION 2: NEGATIVE TEST (Blank/Invalid CPF Validation) ---

        // Re-open Edit Profile page (Find element again to avoid StaleElementReference)
        WebElement menuBtn2 = driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário']"));
        jsClick(menuBtn2);
        clickElement(By.linkText("Editar"));

        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pessoa.cpf")));

        // 2a. Blank CPF Test
        cpfInput.click();
        cpfInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        cpfInput.sendKeys(Keys.BACK_SPACE);

        // Click Save (Expect validation error)
        WebElement saveButton2 = driver.findElement(By.xpath("//button[contains(.,'Salvar')]"));
        jsClick(saveButton2);

        // 2b. Invalid CPF Test
        cpfInput.sendKeys("111.111.111-11");
        jsClick(saveButton2);

        // --- SECTION 3: BOUNDARY & NEGATIVE (Date of Birth) ---

        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pessoa.dataNascimento")));

        // 3a. Invalid Date
        dateInput.click();
        dateInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        dateInput.sendKeys(Keys.BACK_SPACE);
        dateInput.sendKeys("00/00/0000");
        jsClick(saveButton2);

        // 3b. Boundary Date
        dateInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        dateInput.sendKeys(Keys.BACK_SPACE);
        dateInput.sendKeys("01/01/0001");
        jsClick(saveButton2);

        // --- SECTION 4: NEGATIVE (Email Format) ---

        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));

        // 4a. Invalid Email 1
        emailInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        emailInput.sendKeys(Keys.BACK_SPACE);
        emailInput.sendKeys("mail123");
        jsClick(saveButton2);

        // 4b. Invalid Email 2
        emailInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        emailInput.sendKeys(Keys.BACK_SPACE);
        emailInput.sendKeys("mail123@");
        jsClick(saveButton2);

        // --- FINAL CHECK: Verify Persistence ---

        // Re-open Edit Profile page one last time
        WebElement menuBtnFinal = driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário']"));
        jsClick(menuBtnFinal);
        clickElement(By.linkText("Editar"));

        // Final Assertion: Verify name matches RANDOM string we generated at the start
        WebElement finalNameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        assertEquals("Name was not successfully updated", randomName.toUpperCase(), finalNameInput.getAttribute("value"));
    }
}