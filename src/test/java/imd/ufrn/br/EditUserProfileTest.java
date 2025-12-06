package imd.ufrn.br;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class EditUserProfileTest {
    private WebDriver driver;
    private WebDriverWait wait;
    private final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private final String BASE_URL = "https://toronto.imd.ufrn.br/gestao/externo/listar-empresas";

    // --- Setup and Teardown ---

    @Before
    public void setUp() {
        // NOTE: Ensure your geckodriver is correctly configured in your system PATH
        // or set the system property here:
        // System.setProperty("webdriver.gecko.driver", "/path/to/geckodriver");
        driver = new FirefoxDriver();
        driver.manage().window().setSize(new Dimension(602, 772));
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // --- Helper Methods ---

    /** Waits for element to be clickable and clicks it. */
    private void clickElement(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    /** Waits for and closes the toast notification. */
    private void closeToastNotification() {
        // Uses two common close icons based on your recording
        clickElement(By.cssSelector(".p-toast-icon-close-icon, .p-toast-icon-close"));
    }

    // --- Test Method ---

    @Test
    public void editUserProfile() {
        driver.get(BASE_URL);

        // --- SECTION 1: POSITIVE TEST (Valid Name Update) ---

        // Open Edit Profile page
        clickElement(By.cssSelector(".fa-user"));
        clickElement(By.linkText("Editar"));

        // Update Name
        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        nameInput.click();
        nameInput.clear(); // Ensure field is empty before typing
        nameInput.sendKeys("TESTEEDIT123");

        // Save
        clickElement(By.cssSelector(".btn-primary"));

        // ASSERTION: Verify Success Toast Appears (Positive Test)
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-success")));
        closeToastNotification();

        // --- SECTION 2: NEGATIVE TEST (Blank/Invalid CPF Validation) ---

        // Re-open Edit Profile page
        clickElement(By.cssSelector(".fa-user"));
        clickElement(By.linkText("Editar"));

        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pessoa.cpf")));

        // 2a. Blank CPF Test
        cpfInput.click();
        cpfInput.clear(); // Using clear() is the best way to test for blank submission
        clickElement(By.cssSelector(".btn-primary"));

        // ASSERTION: Verify Validation Error for Required Field (Negative Test)
        // NOTE: You must identify the actual error message locator for this to pass.
        // Example: assertTrue("Missing CPF required error", driver.findElement(By.cssSelector(".p-error")).isDisplayed());

        // 2b. Invalid CPF Test (using the value from your original script)
        cpfInput.sendKeys("111.111.111-11"); // Likely an invalid check-digit CPF
        clickElement(By.cssSelector(".btn-primary"));

        // ASSERTION: Verify Validation Error for Invalid Format (Negative Test)
        // Example: assertTrue("Missing CPF format error", driver.findElement(By.cssSelector(".p-error")).isDisplayed());

        // --- SECTION 3: BOUNDARY & NEGATIVE (Date of Birth Validation) ---

        WebElement dateInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pessoa.dataNascimento")));

        // 3a. Invalid Date Test
        dateInput.click();
        dateInput.clear();
        dateInput.sendKeys("00/00/0000");
        clickElement(By.cssSelector(".btn-primary"));
        // ASSERTION: Check for invalid date error

        // 3b. Boundary Date Test
        dateInput.clear();
        dateInput.sendKeys("01/01/0001");
        clickElement(By.cssSelector(".btn-primary"));
        // ASSERTION: Check for success or error based on business rules

        // --- SECTION 4: NEGATIVE (Email Format Validation) ---

        WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-email")));

        // 4a. Invalid Email 1 (Missing @)
        emailInput.clear();
        emailInput.sendKeys("mail123");
        clickElement(By.cssSelector(".btn-primary"));
        // ASSERTION: Check for email format error

        // 4b. Invalid Email 2 (Missing domain)
        emailInput.clear();
        emailInput.sendKeys("mail123@");
        clickElement(By.cssSelector(".btn-primary"));
        // ASSERTION: Check for email format error

        // --- SECTION 5: NEGATIVE (Non-Image File Upload) ---

        // Re-open Edit Profile page
        clickElement(By.cssSelector(".fa-user"));
        clickElement(By.linkText("Editar"));

        WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("formFile")));

        // 5a. Non-Image File Test (MP3 from your script)
        fileInput.sendKeys("C:\\fakepath\\fire-crackling-loop.mp3");
        clickElement(By.cssSelector(".fa-check")); // Click to confirm/submit upload

        // ASSERTION: Check for file type/size validation error toast
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-error")));
        closeToastNotification();

        // 5b. Valid Image Upload (Positive Test)
        fileInput.sendKeys("C:\\fakepath\\user.jpeg");
        clickElement(By.cssSelector(".fa-check"));

        // ASSERTION: Check for file upload success toast
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".p-toast-message-success")));
        closeToastNotification();

        // --- Final Check: Verify Persistence of Changes (e.g., Name) ---

        // Re-open Edit Profile page one last time
        clickElement(By.cssSelector(".fa-user"));
        clickElement(By.linkText("Editar"));

        // Final Assertion: Verify the name field still holds the successfully updated value
        WebElement finalNameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        assertEquals("Name was not successfully updated", "TESTEEDIT123", finalNameInput.getAttribute("value"));
    }
}