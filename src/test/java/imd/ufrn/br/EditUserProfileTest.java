package imd.ufrn.br;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import io.github.cdimascio.dotenv.Dotenv;

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
        System.setProperty("webdriver.http.timeout", "30000");
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");

        driver = new ChromeDriver(options);
        driver.manage().window().setSize(new Dimension(1920, 1080));
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);
    }

    @After
    public void tearDown() {
        if (driver != null) driver.quit();
    }

    // --- Helpers ---

    private void clickElement(By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    private void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    // --- Tests ---

    @Test
    public void editUserProfile() {
        // 1. Login
        driver.get(LOGIN_URL);

        // Load the .env file
        Dotenv dotenv = Dotenv.load();

        // Get credentials from the file
        String myUsername = dotenv.get("TEST_USER");
        String myPassword = dotenv.get("TEST_PASS");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(myUsername);
        driver.findElement(By.id("password")).sendKeys(myPassword);
        driver.findElement(By.id("password")).submit();

        // 2. Wait for Dashboard Load
        wait.until(ExpectedConditions.urlContains("listar-empresas"));
        WebElement menuButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button[aria-label='Abrir menu do usuário']")));

        // 3. Generate Random Name
        String randomName = "User_" + UUID.randomUUID().toString().substring(0, 8);

        // --- SECTION 1: Valid Name Update ---
        jsClick(menuButton);
        clickElement(By.linkText("Editar"));

        WebElement nameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE); // Robust clear
        nameInput.sendKeys(randomName);

        WebElement saveButton = driver.findElement(By.xpath("//button[contains(.,'Salvar')]"));
        jsClick(saveButton);

        // Wait for success toast to disappear to prevent click interception later
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".p-toast-message-text")));
        } catch (Exception ignored) {}

        // --- SECTION 2: Negative Test (Blank CPF) ---
        // Re-open menu (find element again to avoid stale reference)
        jsClick(driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
        clickElement(By.linkText("Editar"));

        WebElement cpfInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("pessoa.cpf")));
        cpfInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.BACK_SPACE);

        jsClick(driver.findElement(By.xpath("//button[contains(.,'Salvar')]")));

        // Assert: Ensure we are still on the edit page (save didn't redirect) or check for error class
        // assertTrue(driver.findElement(By.cssSelector(".is-invalid")).isDisplayed()); // Example if class exists

        // --- FINAL CHECK: Persistence ---
        jsClick(driver.findElement(By.cssSelector("button[aria-label='Abrir menu do usuário']")));
        clickElement(By.linkText("Editar"));

        WebElement finalNameInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("input-nome")));
        assertEquals("Name check failed", randomName.toUpperCase(), finalNameInput.getAttribute("value"));
    }
}