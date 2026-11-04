package com.hrm.employee;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private HrmApplication session;
    private HrmApi api;
    private boolean busy = false;
    private int screenVersion = 0;
    private TextInputEditText username, password;
    private TextInputLayout usernameLayout, passwordLayout;
    private TextView errorText;
    private View progress;
    private android.widget.Button submit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.enableEdgeToEdge(getWindow());
        session = (HrmApplication) getApplication();
        api = new HrmApi(getString(R.string.api_base_url));
        if (session.token() == null)
            showLogin();
        else {
            restoreSession();
        }
    }

    private void content(int layout) {
        setContentView(layout);
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void showLogin() {
        ++screenVersion;
        busy = false;
        content(R.layout.activity_main);
        username = findViewById(R.id.loginUsername);
        password = findViewById(R.id.loginPassword);
        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        errorText = findViewById(R.id.loginError);
        progress = findViewById(R.id.loginProgress);
        submit = findViewById(R.id.loginButton);
        submit.setOnClickListener(v -> login());
        password.setOnEditorActionListener((v, id, event) -> {
            if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });
    }

    private void login() {
        if (busy)
            return;
        usernameLayout.setError(null);
        passwordLayout.setError(null);
        errorText.setVisibility(View.GONE);
        String user = username.getText() == null ? "" : username.getText().toString().trim(),
                pass = password.getText() == null ? "" : password.getText().toString();
        if (user.isEmpty()) {
            usernameLayout.setError("Nhập tên đăng nhập");
            username.requestFocus();
            return;
        }
        if (pass.isEmpty()) {
            passwordLayout.setError("Nhập mật khẩu");
            password.requestFocus();
            return;
        }
        busy = true;
        submit.setEnabled(false);
        username.setEnabled(false);
        password.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        int version = screenVersion;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null)
            keyboard.hideSoftInputFromWindow(password.getWindowToken(), 0);
        network.execute(() -> {
            try {
                JSONObject response = api.login(user, pass);
                String token = response.getString("accessToken");
                if (token.isEmpty())
                    throw new IllegalStateException();
                JSONObject profile = api.me(token);
                if (!"EMPLOYEE".equals(profile.optString("role")))
                    throw new HrmApi.ApiException(403,
                            "Ứng dụng này dành cho tài khoản EMPLOYEE. ADMIN/HR vui lòng dùng giao diện web.");
                runOnUiThread(() -> {
                    if (isDestroyed() || version != screenVersion)
                        return;
                    password.setText("");
                    session.signIn(token, profile);
                    showHome();
                });
            } catch (Exception error) {
                String message = error instanceof HrmApi.ApiException && ((HrmApi.ApiException) error).status == 401
                        ? "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản không còn hoạt động."
                        : HrmApi.message(error);
                runOnUiThread(() -> {
                    if (isDestroyed() || version != screenVersion)
                        return;
                    busy = false;
                    progress.setVisibility(View.GONE);
                    submit.setEnabled(true);
                    username.setEnabled(true);
                    password.setEnabled(true);
                    errorText.setText(message);
                    errorText.setVisibility(View.VISIBLE);
                    password.requestFocus();
                });
            }
        });
    }

    private void showHome() {
        ++screenVersion;
        startActivity(new android.content.Intent(this,EmployeeActivity.class));
        finish();
    }
    private void restoreSession() {
        String token=session.token();showLogin();busy=true;submit.setEnabled(false);progress.setVisibility(View.VISIBLE);int version=screenVersion;
        network.execute(()->{try{JSONObject profile=api.me(token);if(!"EMPLOYEE".equals(profile.optString("role")))throw new HrmApi.ApiException(403,"Ứng dụng chỉ dành cho EMPLOYEE.");runOnUiThread(()->{if(isDestroyed()||version!=screenVersion)return;session.signIn(token,profile);showHome();});}catch(Exception error){runOnUiThread(()->{if(isDestroyed()||version!=screenVersion)return;session.clearSession();busy=false;submit.setEnabled(true);progress.setVisibility(View.GONE);errorText.setText(HrmApi.message(error));errorText.setVisibility(View.VISIBLE);});}});
    }

    private String value(JSONObject profile, String key) {
        String value = profile.optString(key, "");
        return value.isEmpty() || "null".equals(value) ? "Chưa có thông tin" : value;
    }

    private void renderProfile() {
        JSONObject p = session.profile();
        if (p == null)
            return;
        ((TextView) findViewById(R.id.employeeGreeting)).setText("Xin chào, " + value(p, "fullName"));
        ((TextView) findViewById(R.id.employeeCode)).setText(value(p, "employeeCode"));
        ((TextView) findViewById(R.id.employeeUsername)).setText(value(p, "username"));
        ((TextView) findViewById(R.id.employeeDepartment)).setText(value(p, "departmentName"));
        ((TextView) findViewById(R.id.employeePosition)).setText(value(p, "positionName"));
        ((TextView) findViewById(R.id.employeeEmail)).setText(value(p, "email"));
    }

    private void homeBusy(boolean value) {
        busy = value;
        findViewById(R.id.homeProgress).setVisibility(value ? View.VISIBLE : View.GONE);
        findViewById(R.id.refreshProfile).setEnabled(!value);
        findViewById(R.id.logoutButton).setEnabled(!value);
    }

    private void refreshProfile() {
        if (busy || session.token() == null)
            return;
        homeBusy(true);
        int version = screenVersion;
        String token = session.token();
        network.execute(() -> {
            try {
                JSONObject p = api.me(token);
                if (!"EMPLOYEE".equals(p.optString("role")))
                    throw new HrmApi.ApiException(403, "Vai trò tài khoản không còn phù hợp với ứng dụng nhân viên.");
                runOnUiThread(() -> {
                    if (isDestroyed() || version != screenVersion)
                        return;
                    session.signIn(token, p);
                    renderProfile();
                    homeBusy(false);
                    findViewById(R.id.homeError).setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || version != screenVersion)
                        return;
                    if (e instanceof HrmApi.ApiException
                            && (((HrmApi.ApiException) e).status == 401 || ((HrmApi.ApiException) e).status == 403)) {
                        session.clearSession();
                        showLogin();
                        errorText.setText(HrmApi.message(e));
                        errorText.setVisibility(View.VISIBLE);
                    } else {
                        homeBusy(false);
                        TextView error = findViewById(R.id.homeError);
                        error.setText(HrmApi.message(e));
                        error.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void logout() {
        if (busy || session.token() == null)
            return;
        homeBusy(true);
        int version = screenVersion;
        String token = session.token();
        network.execute(() -> {
            boolean serverLoggedOut = true;
            try {
                api.logout(token);
            } catch (Exception ignored) {
                serverLoggedOut = false;
            }
            boolean ok = serverLoggedOut;
            runOnUiThread(() -> {
                if (isDestroyed() || version != screenVersion)
                    return;
                session.clearSession();
                showLogin();
                if (!ok) {
                    errorText.setText(
                            "Đã đăng xuất khỏi ứng dụng. Không liên hệ được máy chủ để kết thúc phiên trên máy chủ.");
                    errorText.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        ++screenVersion;
        network.shutdownNow();
        super.onDestroy();
    }
}
