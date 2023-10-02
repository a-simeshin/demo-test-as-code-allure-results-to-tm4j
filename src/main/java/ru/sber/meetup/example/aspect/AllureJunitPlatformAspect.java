package ru.sber.meetup.example.aspect;

import io.qameta.allure.Allure;
import io.qameta.allure.internal.AllureStorage;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.TestResult;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.platform.launcher.TestIdentifier;

@Aspect
public class AllureJunitPlatformAspect {

    private final List<TestResult> fromAllure = new ArrayList<>();

    /**
     * все junit5 тесты модуля завершились
     */
    @Pointcut("execution (void io.qameta.allure.junitplatform.AllureJunitPlatform.testPlanExecutionFinished(..))")
    public void testPlanExecutionFinished() {}

    /**
     * junit5 тест завершился
     */
    @Pointcut("execution (void io.qameta.allure.junitplatform.AllureJunitPlatform.executionFinished(..))")
    public void testCaseExecutionFinished() {}

    @SneakyThrows
    @Before(value = "testCaseExecutionFinished()")
    public void beforeTestCaseExecutionFinished(final JoinPoint jointPoint) {
        // TestIdentifier содержит всю информацию о junit5 тесте
        final TestIdentifier testIdentifier = (TestIdentifier) jointPoint.getArgs()[0];
        // Если это контейнер, например Nested Test, то нас он не интересует, нужны реальные тесты с шагами Allure
        if (testIdentifier.isContainer()) return;

        // Получаем текущий идентификатор Allure UUID для завершающегося теста
        final Optional<String> allureUUID = Allure.getLifecycle().getCurrentTestCase();
        // Если результата теста нет, значит allure настроен некорректно
        if (allureUUID.isEmpty()) return;

        // Достаем AllureStorage, где хранятся данные Allure по заверащающемуся тесту
        final Field fieldAllureStorage = Allure.getLifecycle().getClass().getDeclaredField("storage");
        fieldAllureStorage.setAccessible(true);
        final AllureStorage allureStorage = (AllureStorage) fieldAllureStorage.get(Allure.getLifecycle());

        // Достаем результат по uuid из AllureStorage
        // Нужно доставать именно сейчас, так как в методе поинтката результаты будут записаны в allure-results
        // а самое печальное, что uuid после этого будет утерян и не будет ссылаться на реальный объект в ALlureStorage
        final Optional<TestResult> testResult = allureStorage.getTestResult(allureUUID.get());
        testResult.ifPresent(fromAllure::add);
    }

    /**
     * Для каждого успешно пройденного теста нужно постучаться в API, сравнить, что есть там и что нужно сделать
     */
    @SneakyThrows
    @Before(value = "testCaseExecutionFinished()")
    public void beforeTestPlanExecutionFinished() {
        fromAllure.parallelStream()
                .filter(allureResult -> allureResult.getStatus().equals(Status.PASSED))
                .forEach(allureResult -> {
                    // У каждого своя TMS и вариант интеграции с API, так что вот этот код нужно написать самому :)
                    // 1. Ищем в TMS по имени Allure кейса (возможно с именами параметризированных тестов надо будет
                    // поколдовать)
                    // 1.1 Сравниваем шаги и описание которые есть в Allure и те, которые есть в TMS
                    // 1.2 Если шаги или описание отличается, обновляем в TMS
                    // 2. В случае когда тест по имени Allure в TMS не найден, надо тест создать с описанием и шагами
                    // PROFIT
                });
    }
}
