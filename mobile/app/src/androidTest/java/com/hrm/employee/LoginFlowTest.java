package com.hrm.employee;

import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.textfield.TextInputLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class LoginFlowTest {
    private void clearSession() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ((HrmApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext()).clearSession());
    }

    private void waitForError(ActivityScenario<MainActivity> scenario) {
        long deadline = SystemClock.elapsedRealtime() + 30000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(a -> {
                TextView error = a.findViewById(R.id.loginError);
                ready.set(error != null && error.getVisibility() == View.VISIBLE
                        && !error.getText().toString().isEmpty());
            });
            if (ready.get())
                return;
            SystemClock.sleep(100);
        }
        fail("No login error displayed within timeout");
    }

    @Test
    public void blankFieldsFocusCorrectInput() {
        clearSession();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.loginButton)).perform(click());
            scenario.onActivity(a -> {
                assertEquals("Nhập tên đăng nhập",
                        ((TextInputLayout) a.findViewById(R.id.usernameLayout)).getError().toString());
                assertTrue(a.findViewById(R.id.loginUsername).hasFocus());
            });
            onView(withId(R.id.loginUsername)).perform(replaceText("employee.test"), closeSoftKeyboard());
            onView(withId(R.id.loginButton)).perform(click());
            scenario.onActivity(a -> {
                assertEquals("Nhập mật khẩu",
                        ((TextInputLayout) a.findViewById(R.id.passwordLayout)).getError().toString());
                assertTrue(a.findViewById(R.id.loginPassword).hasFocus());
            });
        }
    }

    @Test
    public void unknownAccountShowsBackendLoginError() {
        clearSession();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.loginUsername)).perform(replaceText("android-smoke-" + UUID.randomUUID()));
            onView(withId(R.id.loginPassword)).perform(replaceText("invalid-test-only"), closeSoftKeyboard());
            onView(withId(R.id.loginButton)).perform(click());
            waitForError(scenario);
            onView(withId(R.id.loginError)).check(
                    matches(withText("Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản không còn hoạt động.")));
            onView(withId(R.id.loginButton)).check(matches(isEnabled()));
            onView(withId(R.id.employeeGreeting)).check(doesNotExist());
        }
    }

    @Test
    public void invalidSessionReturnsToLogin() {
        clearSession();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                ((HrmApplication) InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getApplicationContext()).signIn("invalid-test-token",
                                new org.json.JSONObject().put("role", "EMPLOYEE").put("fullName", "Kiểm thử"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitForError(scenario);
            onView(withId(R.id.loginUsername)).check(matches(isDisplayed()));
            scenario.onActivity(a -> assertNull(((HrmApplication) a.getApplication()).token()));
        }
    }
}
