package com.hrm.employee;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Personal mobile workspace. Backend capabilities drive every record action.
 */
public class EmployeeActivity extends AppCompatActivity {
    private static final int BLUE = 0xff004581, INK = 0xff163d5c, MUTED = 0xff6d8196;
    private TextInputEditText leaveEvidenceTarget;private boolean evidenceUploading;
    private final androidx.activity.result.ActivityResultLauncher<String> leaveEvidencePicker=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),uri->{
        final TextInputEditText target=leaveEvidenceTarget;if(uri==null||target==null)return;evidenceUploading=true;target.setText("Đang tải minh chứng…");
        this.executor.execute(()->{try{byte[] bytes;try(java.io.InputStream input=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){if(input==null)throw new java.io.IOException("Không mở được tệp");byte[] buffer=new byte[8192];int size;while((size=input.read(buffer))!=-1){if(output.size()+size>3*1024*1024)throw new java.io.IOException("Minh chứng tối đa 3 MB");output.write(buffer,0,size);}bytes=output.toByteArray();}
            JSONObject uploaded=this.api.send("/employee-requests/evidence","POST",this.session.token(),json("content",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)));
            runOnUiThread(()->{evidenceUploading=false;if(target==leaveEvidenceTarget){target.setText(uploaded.optString("reference"));target.setError(null);toast("Đã đính kèm minh chứng");}});
        }catch(Exception error){runOnUiThread(()->{evidenceUploading=false;if(target==leaveEvidenceTarget){target.setText("");target.setError(HrmApi.message(error));target.requestFocus();}});}});
    });
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private HrmApplication session;
    private HrmApi api;
    private MobileNotifications notifications;
    private LinearLayout body;
    private BottomNavigationView navigation;
    private View loading;
    private String section = "home", category = "regular";
    private int generation = 0, page = 0;
    private boolean writing = false;
    private int pendingReads = 0;
    private LocalDate from = today().minusDays(7), to = today().plusDays(30);
    private long selectedPayslipId = 0L;

    private interface Reply {
        void accept(JSONObject value) throws Exception;
    }

    private interface Work {
        JSONObject run() throws Exception;
    }

    public boolean showNotificationPage(JSONObject target){
        JSONObject detail=target.optJSONObject("detail");String kind=target.optString("referenceType");
        if(kind.equals("payslips")){selectedPayslipId=target.optLong("payslipId",target.optLong("referenceId",0L));section="salary";render();return true;}
        if(kind.equals("overtime_requests")){navigation.setSelectedItemId(R.id.navRequests);category="ot";render();fetch("/api/overtime/workspace/requests/"+target.optLong("referenceId"),this::otDetail);return true;}
        if(kind.equals("attendance_records")){category="regular";navigation.setSelectedItemId(R.id.navAttendance);long schedule=target.optLong("scheduleId");fetch("/api/attendance-workspace/schedules/"+schedule+"?mine=true",this::attendanceDetail);return true;}
        navigation.setSelectedItemId(R.id.navRequests);
        category=kind.equals("attendance_portal_requests")?"regular":detail!=null&&detail.optString("requestType").equals("NGHI_VIEC")?"resignation":"leave";render();return false;
    }
    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        session = (HrmApplication) getApplication();
        if (session.token() == null || session.profile() == null
                || !"EMPLOYEE".equals(session.profile().optString("role"))) {
            signOutLocal();
            return;
        }
        api = new HrmApi(getString(R.string.api_base_url));
        WindowCompat.enableEdgeToEdge(getWindow());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);
        setContentView(R.layout.activity_employee);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, i) -> {
            Insets bars = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return i;
        });
        body = findViewById(R.id.employeeBody);
        loading = findViewById(R.id.employeeLoading);
        navigation = findViewById(R.id.employeeNavigation);
        navigation.setOnItemSelectedListener(item -> {
            if (writing)
                return false;
            int id = item.getItemId();
            section = id == R.id.navSchedule ? "schedule"
                    : id == R.id.navAttendance ? "attendance"
                            : id == R.id.navRequests ? "requests" : id == R.id.navProfile ? "profile" : "home";
            if (id == R.id.navProfile) {
                profileSubPage = "hub";
            }
            category = "regular";
            attendanceCalendarOpen = false;
            page = 0;
            render();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (writing) return;
                if ("profile".equals(section) && "contract_detail".equals(profileSubPage)) {
                    showProfileContracts();
                    return;
                }
                if ("profile".equals(section) && !"hub".equals(profileSubPage)) {
                    profileSubPage = "edit_profile".equals(profileSubPage) && profileEditFromAccountInfo
                            ? "account_info"
                            : "hub";
                    profileEditFromAccountInfo = false;
                    render();
                    return;
                }
                if ("salary".equals(section) && navigation.getSelectedItemId() == R.id.navProfile) {
                    section = "profile";
                    render();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        findViewById(R.id.employeeRefresh).setOnClickListener(v -> {
            if (!writing) {
                if (api != null) api.clearCache();
                render();
            }
        });
        notifications=new MobileNotifications(this,api,session,findViewById(R.id.employeeBell),findViewById(R.id.employeeNotificationBadge));
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(notifications!=null)notifications.start();
        if(body!=null && body.getChildCount()==0 && api!=null && session!=null && session.token()!=null && !writing && pendingReads==0)render();
    }

    @Override protected void onPause(){if(notifications!=null)notifications.stop();super.onPause();}

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold)
            t.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(8);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout card(String title) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        card.setLayoutParams(p);
        card.setRadius(dp(14));
        card.setCardElevation(0);
        card.setStrokeColor(0xffe2e9f2);
        card.setStrokeWidth(dp(1));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout box = column();
        box.setPadding(dp(16), dp(16), dp(16), dp(12));
        if (title != null)
            box.addView(text(title, 16, INK, true));
        card.addView(box);
        body.addView(card);
        return box;
    }

    private void line(LinearLayout box, String label, String value) {
        box.addView(text(label, 11, MUTED, true));
        box.addView(text(value == null || value.isEmpty() ? "—" : value, 14, INK, false));
    }

    private MaterialButton button(LinearLayout box, String title, boolean primary, Runnable click) {
        MaterialButton b = new MaterialButton(this, null,
                primary ? com.google.android.material.R.attr.materialButtonStyle
                        : com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(title);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setTextSize(13);
        b.setAllCaps(false);
        if (primary)
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.topMargin = dp(4);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> {
            if (!writing)
                click.run();
        });
        box.addView(b);
        return b;
    }

    private void badge(LinearLayout box, String value, String state) {
        int color = state.contains("TU_CHOI") || state.contains("VANG") ? 0xffa32b32
                : state.contains("CHO") || state.contains("CHUA") || state.contains("THIEU") || state.contains("DANG_XU_LY") ? 0xff8a5b00
                        : 0xff137957;
        TextView t = text(value, 12, color, true);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color == 0xff137957 ? 0xffe7f5ee : color == 0xffa32b32 ? 0xffffeeee : 0xfffff3d8);
        bg.setCornerRadius(dp(8));
        t.setBackground(bg);
        box.addView(t);
    }

    private String str(JSONObject o, String key) {
        String s = o.optString(key, "");
        return s.equals("null") ? "" : s;
    }

    private String time(String value) {
        if (value == null || value.isEmpty())
            return "Chưa ghi nhận";
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return value;
        }
    }

    private String utc(String value) {
        if (value == null || value.isEmpty())
            return "—";
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return value;
        }
    }

    private String label(String value) {
        switch (value) {
            case "OT_REGISTER":
                return "Gửi đơn OT";
            case "OT_EDIT":
                return "Sửa đơn OT";
            case "OT_APPROVE":
                return "Duyệt đơn";
            case "OT_REJECT":
                return "Từ chối đơn";
            case "OT_ACTUAL":
                return "Ghi nhận giờ thực tế";
            case "OT_CONFIRM":
                return "Xác nhận giờ OT";
            case "OT_CANCEL":
                return "Hủy đơn";
            case "OT_SELF_CHECK_IN":
                return "Tự check-in OT";
            case "OT_SELF_CHECK_OUT":
                return "Tự check-out OT";
            case "OT_SUPPLEMENT_SUBMIT":
                return "Gửi bổ sung công OT";
            case "OT_SUPPLEMENT_APPROVE":
                return "Duyệt bổ sung OT";
            case "OT_SUPPLEMENT_REJECT":
                return "Từ chối bổ sung OT";
            case "CHO_DUYET":
                return "Chờ duyệt";
            case "DA_DUYET":
                return "Đã duyệt";
            case "TU_CHOI":
                return "Từ chối";
            case "DA_HUY":
                return "Đã hủy";
            case "CHO_XU_LY":
                return "Chờ xử lý";
            case "DANG_XU_LY":
                return "Đã tiếp nhận";
            case "DA_CHAP_NHAN":
                return "Đã chấp nhận";
            case "DA_XAC_NHAN":
                return "Đã xác nhận";
            case "CHUA_XAC_NHAN":
                return "Chưa xác nhận";
            case "DI_LAM":
                return "Đi làm";
            case "VANG":
                return "Vắng";
            case "MISSING_ATTENDANCE":
                return "Bổ sung giờ vào/ra";
            case "MISSING_CHECKOUT":
                return "Bổ sung giờ ra";
            case "UNCONFIRMED_CORRECTION":
                return "Sửa công chưa xác nhận";
            case "CONFIRMED_CORRECTION":
                return "Điều chỉnh công";
            default:
                return value.isEmpty() ? "Chưa có công" : value;
        }
    }

    private void title(String title, String subtitle) {
        TextView h = text(title, 24, INK, true);
        h.setAccessibilityHeading(true);
        body.addView(h);
        body.addView(text(subtitle, 13, MUTED, false));
    }

    private String query() {
        return "from=" + from + "&to=" + to + "&page=" + page;
    }

    private void range() {
        LinearLayout c = card("Khoảng ngày đang xem");
        button(c, from.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "  →  "
                + to.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), false, () -> pickDate(from, d -> {
                    from = d;
                    pickDate(to, z -> {
                        to = z;
                        page = 0;
                        if (to.isBefore(from) || to.isAfter(from.plusDays(366))) {
                            toast("Chọn khoảng ngày hợp lệ, tối đa 366 ngày.");
                            return;
                        }
                        render();
                    });
                }));
    }

    private void pickDate(LocalDate date, java.util.function.Consumer<LocalDate> done) {
        new DatePickerDialog(this, (p, y, m, d) -> done.accept(LocalDate.of(y, m + 1, d)), date.getYear(),
                date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private void tabs(String[] names, String[] keys) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
            b.setText(names[i]);
            b.setTextColor(key.equals(category) ? Color.WHITE : BLUE);
            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(key.equals(category) ? BLUE : 0xffe8f1fa));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44));
            p.setMarginEnd(dp(8));
            b.setLayoutParams(p);
            b.setOnClickListener(v -> {
                if (!writing) {
                    category = key;
                    page = 0;
                    render();
                }
            });
            row.addView(b);
        }
        scroll.addView(row);
        body.addView(scroll);
    }

    private void pages(JSONObject data) {
        int current = data.optInt("page"), total = data.optInt("totalPages");
        if (total <= 1)
            return;
        LinearLayout box = card("Trang " + (current + 1) + " / " + total);
        if (current > 0)
            button(box, "‹ Trang trước", false, () -> {
                page = current - 1;
                render();
            });
        if (current + 1 < total)
            button(box, "Trang sau ›", false, () -> {
                page = current + 1;
                render();
            });
    }

    private void fetch(String path, Reply reply) {
        fetch(path, reply, this::error);
    }

    private void fetch(String path, Reply reply, java.util.function.Consumer<String> onError) {
        int version = generation;
        ++pendingReads;
        loading.setVisibility(View.VISIBLE);
        String token = session.token();
        executor.execute(() -> {
            try {
                JSONObject data = api.get(path, token);
                runOnUiThread(() -> {
                    if (isDestroyed() || version != generation)
                        return;
                    pendingReads = Math.max(0, pendingReads - 1);
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    try {
                        reply.accept(data);
                    } catch (Exception e) {
                        onError.accept("Dữ liệu phản hồi không hợp lệ.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || version != generation)
                        return;
                    pendingReads = Math.max(0, pendingReads - 1);
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    if (e instanceof HrmApi.ApiException && ((HrmApi.ApiException) e).status == 401) {
                        session.clearSession();
                        signOutLocal();
                    } else
                        onError.accept(HrmApi.message(e));
                });
            }
        });
    }

    private void error(String message) {
        LinearLayout box = card("Chưa tải được dữ liệu");
        box.addView(text(message, 14, 0xffa32b32, false));
        button(box, "Thử lại", false, this::render);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private void write(String path, String method, JSONObject payload, String message,
            androidx.appcompat.app.AlertDialog form) {
        if (writing)
            return;
        new MaterialAlertDialogBuilder(this).setTitle("Xác nhận thao tác").setMessage(message)
                .setNegativeButton("Hủy", null).setPositiveButton("Xác nhận", (dialog, which) -> {
                    writing = true;
                    loading.setVisibility(View.VISIBLE);
                    navigation.setEnabled(false);
                    String token = session.token();
                    executor.execute(() -> {
                        try {
                            api.send(path, method, token, payload);
                            runOnUiThread(() -> {
                                if (isDestroyed())
                                    return;
                                writing = false;
                                navigation.setEnabled(true);
                                loading.setVisibility(View.GONE);
                                if (form != null)
                                    form.dismiss();
                                toast("Đã thực hiện thành công.");
                                render();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                if (isDestroyed())
                                    return;
                                writing = false;
                                navigation.setEnabled(true);
                                loading.setVisibility(View.GONE);
                                if (e instanceof HrmApi.ApiException && ((HrmApi.ApiException) e).status == 401) {
                                    session.clearSession();
                                    signOutLocal();
                                } else
                                    new MaterialAlertDialogBuilder(this).setTitle("Chưa thực hiện được")
                                            .setMessage(HrmApi.message(e)).setPositiveButton("Đóng", null).show();
                            });
                        }
                    });
                }).show();
    }

    private void render() {
        ++generation;
        pendingReads = 0;
        body.removeAllViews();
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        findViewById(R.id.employeeHeader)
                .setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
        ((NestedScrollView) findViewById(R.id.employeeScroll)).scrollTo(0, 0);
        switch (section) {
            case "schedule":
                schedule();
                break;
            case "attendance":
                attendance();
                break;
            case "summary":
                attendanceSummary();
                break;
            case "requests":
                requests();
                break;
            case "profile":
                profile();
                break;
            case "salary":
                salary();
                break;
            default:
                home();
        }
    }

    private void home() {
        JSONObject p = session.profile();
        title("Xin chào, " + str(p, "fullName"),
                today().format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new Locale("vi", "VN"))));
        LinearLayout welcome = card("Không gian cá nhân");
        badge(welcome, str(p, "employeeCode") + " · Nhân viên", "DA_XAC_NHAN");
        welcome.addView(text(str(p, "departmentName") + " · " + str(p, "positionName"), 14, MUTED, false));
        LinearLayout quick = card("Thao tác nhanh");
        button(quick, "Lịch làm việc của tôi", true, () -> navigation.setSelectedItemId(R.id.navSchedule));
        button(quick, "Chấm công / check-in", false, () -> navigation.setSelectedItemId(R.id.navAttendance));
        button(quick, "Đăng ký và theo dõi OT", false, () -> {
            navigation.setSelectedItemId(R.id.navRequests);
            category = "ot";
            page = 0;
            render();
        });
        button(quick, "Phiếu lương của tôi", false, () -> {
            section = "salary";
            render();
        });
        fetch("/api/attendance-workspace/records?mine=true&from=" + today() + "&to=" + today() + "&page=0", data -> {
            JSONObject stats = data.optJSONObject("stats");
            if (stats != null) {
                LinearLayout box = card("Công hôm nay · " + today().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                line(box, "Đã xác nhận", String.valueOf(stats.getLong("confirmed")));
                line(box, "Chờ xác nhận", String.valueOf(stats.getLong("waiting")));
                box.addView(
                        text("Chỉ tính công thường của hôm nay. Công các ngày khác và OT không nằm trong thống kê này.",
                                12, MUTED, false));
                if (data.getLong("totalElements") == 0)
                    box.addView(text("Hôm nay chưa có lịch công thường trong dữ liệu.", 12, MUTED, false));
            }
            JSONArray items = data.optJSONArray("items");
            if (items == null || items.length() == 0)
                card("Ca làm việc hôm nay").addView(text("Hôm nay bạn chưa có lịch làm việc.", 14, MUTED, false));
            else
                for (int i = 0; i < items.length(); i++)
                    attendanceCard(items.getJSONObject(i));
        });
    }

    private final SchedulePeriod schedulePeriod = new SchedulePeriod(today());
    private String scheduleFilter = "all", attendanceFilter = "all";
    private final SchedulePeriod attendancePeriod = new SchedulePeriod(today());
    private static final int CALENDAR_BLUE = 0xff28358a;

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private MaterialButton calendarButton(String label, Runnable action) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setTextColor(CALENDAR_BLUE);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setOnClickListener(v -> {
            if (!writing)
                action.run();
        });
        return b;
    }

    private List<JSONObject> attendanceLeaves = new ArrayList<>();
    private Map<Long,String> attendanceLeaveTypes = new HashMap<>();
    private Set<String> attendanceHolidays = new HashSet<>();
    private YearMonth attendanceMonth = YearMonth.from(today());
    private boolean attendanceMonthView = true;
    private boolean attendanceCalendarOpen = false;
    private LocalDate attendanceSelectedDay = today();
    private String attendanceStateFilter = "all";

    private void attendanceMonthCalendar() {
        button(body, "‹ Quay lại chấm công", false, () -> { attendanceCalendarOpen = false; render(); });
        title("Lịch xem công", "Chọn ngày để xem ca, giờ vào/ra và gửi yêu cầu hợp lệ.");
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton prev = calendarButton("‹", () -> moveAttendanceCalendar(-1));
        toolbar.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(48)));
        MaterialButton month = calendarButton("Tháng " + attendanceMonth.getMonthValue() + "/" + attendanceMonth.getYear(), () -> pickDate(attendanceSelectedDay, d -> {
            attendanceSelectedDay = d;
            attendanceMonth = YearMonth.from(d);
            render();
        }));
        month.setContentDescription("Chọn nhanh tháng và năm");
        toolbar.addView(month, new LinearLayout.LayoutParams(0, dp(48), 1));
        toolbar.addView(calendarButton("›", () -> moveAttendanceCalendar(1)), new LinearLayout.LayoutParams(dp(44), dp(48)));
        body.addView(toolbar);
        LinearLayout modes = new LinearLayout(this);
        for (boolean monthly : new boolean[]{true, false}) {
            MaterialButton b = calendarButton(monthly ? "Theo tháng" : "Theo tuần", () -> { attendanceMonthView = monthly; render(); });
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(attendanceMonthView == monthly ? CALENDAR_BLUE : 0xffe8ecf8));
            b.setTextColor(attendanceMonthView == monthly ? Color.WHITE : CALENDAR_BLUE);
            modes.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        body.addView(modes);
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        String[] keys = {"all", "late", "missing", "absent", "leave", "maternity", "holiday", "ot"};
        String[] names = {"Tất cả", "Muộn / sớm", "Chưa chấm công", "Nghỉ không phép", "Nghỉ phép", "Thai sản", "Ngày lễ", "Tăng ca (OT)"};
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            MaterialButton b = calendarButton(names[i], () -> { attendanceStateFilter = key; render(); });
            b.setStrokeWidth(dp(1));
            b.setStrokeColor(android.content.res.ColorStateList.valueOf(0xffcbd2ed));
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(key.equals(attendanceStateFilter) ? CALENDAR_BLUE : 0xfff1f4fa));
            b.setTextColor(key.equals(attendanceStateFilter) ? Color.WHITE : CALENDAR_BLUE);
            filters.addView(b);
        }
        filterScroll.addView(filters);
        body.addView(filterScroll);
        LinearLayout kinds = new LinearLayout(this);
        String[] kindsKeys = {"all", "regular", "ot"}, kindNames = {"Tất cả ca", "Ca thường", "OT"};
        for (int i = 0; i < kindsKeys.length; i++) {
            String key = kindsKeys[i];
            MaterialButton b = calendarButton(kindNames[i], () -> { attendanceFilter = key; render(); });
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(attendanceFilter.equals(key) ? CALENDAR_BLUE : 0xffe8ecf8));
            b.setTextColor(attendanceFilter.equals(key) ? Color.WHITE : CALENDAR_BLUE);
            kinds.addView(b, new LinearLayout.LayoutParams(0, dp(40), 1));
        }
        body.addView(kinds);
        LocalDate first = attendanceMonthView ? attendanceMonth.atDay(1) : attendanceSelectedDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = attendanceMonthView ? attendanceMonth.atEndOfMonth() : first.plusDays(6);
        LinearLayout content = column();
        body.addView(content);
        content.addView(text("Đang tải lịch công…", 14, MUTED, false));
        int version = generation;
        String token = session.token(), kind = attendanceFilter;
        ++pendingReads;
        loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                List<JSONObject> entries = new ArrayList<>();
                if (!kind.equals("ot")) calendarPages("/api/attendance-workspace/records?mine=true&from=" + first.minusDays(1) + "&to=" + last, "regular", token, entries);
                if (!kind.equals("regular")) calendarPages("/api/overtime/workspace/calendar?mine=true&from=" + first.minusDays(1) + "&to=" + last, "ot", token, entries);
                List<JSONObject> leaves = new ArrayList<>(); Map<Long,String> types = new HashMap<>(); Set<String> holidays = new HashSet<>();
                String warning = "";
                try {
                    for(int p=0,total=1;p<total;p++) {
                        JSONObject data=api.get("/employee-requests/mine?type=NGHI_PHEP&status=DA_DUYET&size=100&page="+p,token);
                        JSONArray items=data.getJSONArray("content");total=data.optInt("totalPages",1);
                        for(int i=0;i<items.length();i++){JSONObject l=items.getJSONObject(i);if(str(l,"withdrawnAt").isEmpty()&&str(l,"leaveStart").compareTo(last.plusDays(1)+"T00:00:00")<0&&str(l,"leaveEnd").compareTo(first+"T00:00:00")>0)leaves.add(l);}
                    }
                    for(int p=0,total=1;p<total;p++) {
                        JSONObject data=api.get("/leave-types?size=100&page="+p,token);total=data.optInt("totalPages",1);JSONArray items=data.getJSONArray("content");
                        for(int i=0;i<items.length();i++){JSONObject t=items.getJSONObject(i);types.put(t.optLong("leaveTypeId"),str(t,"typeCode")+" — "+str(t,"typeName"));}
                    }
                }catch(Exception ex){warning="Chưa tải được nghỉ phép / thai sản: "+HrmApi.message(ex);}
                try{JSONObject policy=api.get("/api/attendance-workspace/calendar-policy",token);if(policy.optBoolean("configured")){JSONArray dates=policy.getJSONArray("holidays");for(int i=0;i<dates.length();i++)holidays.add(dates.getString(i));}else warning+=" Chưa có lịch lễ chính thức.";}catch(Exception ex){warning+=" Chưa tải được lịch lễ chính thức.";}
                final String calendarWarning=warning;
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != version) return;
                    --pendingReads;
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    content.removeAllViews();
                    attendanceLeaves=leaves;attendanceLeaveTypes=types;attendanceHolidays=holidays;
                    if(!calendarWarning.isBlank())content.addView(text(calendarWarning,12,MUTED,false));
                    attendanceCalendarGrid(content, first, last, entries);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != version) return;
                    --pendingReads;
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    content.removeAllViews();
                    content.addView(text(HrmApi.message(ex), 14, 0xff9f2534, false));
                    content.addView(calendarButton("Tải lại", this::render));
                    if (ex instanceof HrmApi.ApiException && ((HrmApi.ApiException)ex).status == 401) {session.clearSession(); signOutLocal();}
                });
            }
        });
    }

    private void moveAttendanceCalendar(int n) {
        if (attendanceMonthView) {attendanceMonth = attendanceMonth.plusMonths(n); attendanceSelectedDay = attendanceMonth.atDay(1);}
        else {attendanceSelectedDay = attendanceSelectedDay.plusWeeks(n); attendanceMonth = YearMonth.from(attendanceSelectedDay);}
        render();
    }
    private List<JSONObject> entriesOn(LocalDate day, List<JSONObject> entries) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject e : entries) if (CalendarMonth.overlaps(day, str(e,"calendarStart"), str(e,"calendarEnd"))) out.add(e);
        return out;
    }
    private String calendarRowState(JSONObject e) {
        JSONObject source = e.optJSONObject("source");
        boolean ot = str(e,"kind").equals("ot");
        JSONObject r = ot ? source.optJSONObject("row").optJSONObject("ot") : source;
        if (str(r,"attendanceResult").equals("VANG")) return "absent";
        JSONArray anomalies = r.optJSONArray("anomalies");
        if (!ot && anomalies != null) for (int i=0;i<anomalies.length();i++) if (anomalies.optString(i).equals("DI_MUON") || anomalies.optString(i).equals("VE_SOM")) return "late";
        if (str(r,"actualStart").isEmpty() || str(r,"actualEnd").isEmpty()) return "missing";
        return "normal";
    }
    private String specialLeaveState(JSONObject l){
        String value=java.text.Normalizer.normalize(attendanceLeaveTypes.getOrDefault(l.optLong("leaveTypeId"),""),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('_',' ').toLowerCase(Locale.ROOT);
        if(value.contains("thai san")||value.contains("maternity"))return "maternity";
        if(value.contains("nghi om")||value.contains("sick")||value.isBlank())return "";
        return "leave";
    }
    private String calendarDayState(LocalDate day, List<JSONObject> entries) {
        List<JSONObject> allDaily=entriesOn(day,entries);
        List<JSONObject> daily=new ArrayList<>();
        for(JSONObject e:allDaily)if(!str(e,"kind").equals("ot"))daily.add(e);
        for(JSONObject e:daily)if(calendarRowState(e).equals("normal"))return "normal";
        for(String state:new String[]{"maternity","leave"})for(JSONObject l:attendanceLeaves)if(CalendarMonth.overlaps(day,str(l,"leaveStart"),str(l,"leaveEnd"))&&specialLeaveState(l).equals(state))return state;
        if(allDaily.isEmpty()&&(attendanceHolidays.contains(day.toString())||!VietnameseHoliday.name(day).isEmpty()))return "holiday";
        for (String priority : new String[]{"absent","late"}) for(JSONObject e:daily) if(calendarRowState(e).equals(priority))return priority;
        for(JSONObject e:daily) if(calendarRowState(e).equals("missing"))return "missing";
        return daily.isEmpty()?(allDaily.isEmpty()?"empty":"ot"):"normal";
    }
    private int calendarStateColor(String state) {
        switch(state) {case "ot":return 0xfff08a18;case "normal":return 0xff164c96;case "late":return 0xfff4b52f;case "absent":return 0xffca334b;case "missing":return 0xffe7dfd3;case "leave":return 0xffb9e2ff;case "maternity":return 0xffbf347d;case "holiday":return Color.TRANSPARENT;default:return 0xffcbd0d7;}
    }
    private int calendarStateInk(String state){return state.equals("normal")||state.equals("absent")||state.equals("maternity")?Color.WHITE:INK;}
    private String calendarStateLabel(String state) {
        switch(state) {case "ot":return "Tăng ca (OT)";case "normal":return "Đi làm bình thường";case "late":return "Đi muộn / về sớm";case "absent":return "Nghỉ không phép";case "missing":return "Chưa / thiếu chấm công";case "leave":return "Nghỉ phép";case "maternity":return "Nghỉ thai sản";case "holiday":return "Ngày lễ";default:return "Không có lịch";}
    }

    private void attendanceCalendarGrid(LinearLayout parent, LocalDate first, LocalDate last, List<JSONObject> entries) {
        Set<String> planned = new HashSet<>(), present = new HashSet<>(); int late=0; Set<String> absent = new HashSet<>();
        for(JSONObject e:entries) {
            if(!str(e,"kind").equals("regular")) continue;
            JSONObject r=e.optJSONObject("source"); String date=str(r,"workDate");
            if(date.compareTo(first.toString())<0||date.compareTo(last.toString())>0)continue;
            planned.add(date);
            if(str(r,"attendanceResult").equals("VANG"))absent.add(date);
            if(!str(r,"actualStart").isEmpty()&&!str(r,"attendanceResult").equals("VANG"))present.add(date);
            JSONArray anomalies=r.optJSONArray("anomalies");
            if(anomalies!=null)for(int i=0;i<anomalies.length();i++)if(anomalies.optString(i).equals("DI_MUON")){late++;break;}
        }
        TextView stats=text("Có lịch: "+planned.size()+" ngày  •  Có giờ vào: "+present.size()+" ngày\nĐi muộn: "+late+" lần  •  Vắng: "+absent.size()+" ngày",12,INK,true);
        stats.setPadding(dp(10),dp(10),dp(10),dp(10));parent.addView(stats);
        TextView note=text("Đếm ngày có lịch và ngày có giờ vào, không phải công chuẩn quy đổi.",11,MUTED,false);parent.addView(note);
        LinearLayout headings=new LinearLayout(this);
        for(String s:new String[]{"T2","T3","T4","T5","T6","T7","CN"}) {TextView t=text(s,13,INK,true);t.setGravity(Gravity.CENTER);headings.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));}parent.addView(headings);
        LinearLayout detail=column();
        LocalDate start=attendanceMonthView?CalendarMonth.firstCell(attendanceMonth):first;
        LocalDate finish=attendanceMonthView?CalendarMonth.lastCell(attendanceMonth):last;
        for(LocalDate week=start;!week.isAfter(finish);week=week.plusDays(7)) {
            LinearLayout row=new LinearLayout(this);
            for(int i=0;i<7;i++) {
                LocalDate date=week.plusDays(i);String state=calendarDayState(date,entries);
                TextView cell=text(String.valueOf(date.getDayOfMonth()),18,calendarStateInk(state),true);
                cell.setGravity(Gravity.CENTER);cell.setBackground(rounded(calendarStateColor(state),9));
                if(attendanceHolidays.contains(date.toString())||!VietnameseHoliday.name(date).isEmpty())((android.graphics.drawable.GradientDrawable)cell.getBackground()).setStroke(dp(3),0xffb91c1c);
                List<JSONObject> daily=entriesOn(date,entries);
                if(!daily.isEmpty())cell.setText(date.getDayOfMonth()+"\n"+daily.size()+" ca");
                String holidayName=VietnameseHoliday.name(date);
                if(!holidayName.isEmpty()){
                    String current=cell.getText().toString(),label=current+"\n"+holidayName;
                    android.text.SpannableString styled=new android.text.SpannableString(label);
                    styled.setSpan(new android.text.style.ForegroundColorSpan(0xff8b0000),current.length()+1,label.length(),0);
                    styled.setSpan(new android.text.style.BackgroundColorSpan(0xfffff1f2),current.length()+1,label.length(),0);
                    styled.setSpan(new android.text.style.RelativeSizeSpan(.48f),current.length()+1,label.length(),0);
                    cell.setText(styled);
                }
                cell.setContentDescription(holidayName+" "+date+", "+calendarStateLabel(state)+", "+daily.size()+" ca");
                boolean matches=attendanceStateFilter.equals("all")||state.equals(attendanceStateFilter)||daily.stream().anyMatch(e->calendarRowState(e).equals(attendanceStateFilter));
                cell.setAlpha(date.isBefore(first)||date.isAfter(last)?.3f:matches?1f:.25f);
                cell.setFocusable(true);
                cell.setEnabled(!date.isBefore(first)&&!date.isAfter(last));
                cell.setOnClickListener(v->{attendanceSelectedDay=date;showAttendanceCalendarDay(detail,date,entries);detail.post(()->{detail.requestFocus();NestedScrollView scroll=findViewById(R.id.employeeScroll);android.graphics.Rect rect=new android.graphics.Rect();detail.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(detail,rect);scroll.smoothScrollTo(0,rect.top);});});
                FrameLayout frame=new FrameLayout(this);
                frame.addView(cell,new FrameLayout.LayoutParams(-1,-1));
                LinearLayout dots=new LinearLayout(this);dots.setGravity(Gravity.END);
                Set<String> statuses=new LinkedHashSet<>();
                for(JSONObject e:daily)statuses.add(str(e,"kind").equals("ot")?"ot":calendarRowState(e));
                for(String status:statuses){
                    View dot=new View(this);dot.setBackground(rounded(calendarStateColor(status),6));
                    android.graphics.drawable.GradientDrawable circle=(android.graphics.drawable.GradientDrawable)dot.getBackground();circle.setStroke(dp(1),Color.WHITE);
                    dot.setContentDescription(calendarStateLabel(status));
                    LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(9),dp(9));dotParams.setMarginStart(dp(3));dots.addView(dot,dotParams);
                }
                FrameLayout.LayoutParams dotsParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);dotsParams.setMargins(0,0,dp(5),dp(4));
                dots.setClickable(false);dots.setFocusable(false);dots.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);frame.addView(dots,dotsParams);
                cell.setContentDescription(cell.getContentDescription()+", "+String.join(", ",statuses.stream().map(this::calendarStateLabel).toArray(String[]::new)));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(66),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(frame,p);
            }parent.addView(row);
        }
        attendanceColorLegend(parent);
        parent.addView(text("Thiếu / chưa có công không đồng nghĩa vắng không phép. OT không cộng vào thống kê công thường.",11,MUTED,false));
        parent.addView(detail);
        detail.setFocusableInTouchMode(true);
        if(!attendanceSelectedDay.isBefore(first)&&!attendanceSelectedDay.isAfter(last))showAttendanceCalendarDay(detail,attendanceSelectedDay,entries);
        button(parent,"Tổng công theo tháng / năm",false,()->{section="summary";render();});
    }
    private void attendanceColorLegend(LinearLayout parent) {
        TextView heading=text("Chú thích màu trạng thái công",13,INK,true);
        heading.setPadding(dp(12),dp(10),dp(8),dp(4));
        LinearLayout legendHeader=new LinearLayout(this);
        legendHeader.setGravity(Gravity.CENTER_VERTICAL);
        legendHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if (section.equals("attendance") && !attendanceCalendarOpen) {
            MaterialButton viewCalendar=calendarButton("Xem lịch",()->{
                attendanceSelectedDay=attendancePeriod.anchor();
                attendanceMonth=YearMonth.from(attendanceSelectedDay);
                attendanceMonthView=true;
                attendanceCalendarOpen=true;
                render();
            });
            viewCalendar.setStrokeWidth(dp(1));
            viewCalendar.setStrokeColor(android.content.res.ColorStateList.valueOf(CALENDAR_BLUE));
            viewCalendar.setCornerRadius(dp(8));
            viewCalendar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe8ecf8));
            viewCalendar.setTag("attendance.viewCalendar");
            LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-2,dp(44));
            actionParams.setMarginEnd(dp(8));
            legendHeader.addView(viewCalendar,actionParams);
        }
        parent.addView(legendHeader);
        for(String[] pair:new String[][]{{"normal","late"},{"absent","missing"},{"leave","maternity"},{"holiday","empty"},{"ot"}}){
            LinearLayout row=new LinearLayout(this);
            for(String state:pair){
                TextView legend=text("● "+calendarStateLabel(state),12,calendarStateInk(state),false);
                legend.setPadding(dp(8),dp(8),dp(8),dp(8));
                legend.setBackground(rounded(calendarStateColor(state),7));
                if(state.equals("holiday"))((android.graphics.drawable.GradientDrawable)legend.getBackground()).setStroke(dp(2),0xffa855c7);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
                p.setMargins(dp(4),dp(3),dp(4),dp(3));row.addView(legend,p);
            }parent.addView(row);
        }
    }

    private void showAttendanceCalendarDay(LinearLayout target, LocalDate day, List<JSONObject> entries) {
        target.removeAllViews();
        for(JSONObject l:attendanceLeaves)if(CalendarMonth.overlaps(day,str(l,"leaveStart"),str(l,"leaveEnd"))&&!specialLeaveState(l).isEmpty()){
            String state=specialLeaveState(l);TextView leave=text(calendarStateLabel(state)+" · Đã duyệt\n"+time(str(l,"leaveStart"))+" → "+time(str(l,"leaveEnd")),13,calendarStateInk(state),true);leave.setBackground(rounded(calendarStateColor(state),8));leave.setPadding(dp(10),dp(10),dp(10),dp(10));target.addView(leave);
        }
        calendarDay(target,day,entriesOn(day,entries));
    }

    private void schedule() {
        if (section.equals("attendance") && attendanceCalendarOpen) { attendanceMonthCalendar(); return; }
        final boolean attendanceView = section.equals("attendance");
        final SchedulePeriod schedulePeriod = attendanceView ? attendancePeriod : this.schedulePeriod;
        final String scheduleFilter = attendanceView ? attendanceFilter : this.scheduleFilter;
        final String tagPrefix = attendanceView ? "attendance" : "schedule";
        body.setPadding(0, 0, 0, dp(16));
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12), dp(18), dp(12), dp(20));
        hero.setBackground(rounded(CALENDAR_BLUE, 20));
        MaterialButton back = calendarButton("‹", () -> navigation.setSelectedItemId(R.id.navHome));
        back.setTextColor(Color.WHITE);
        back.setTextSize(32);
        back.setContentDescription("Về trang chủ");
        hero.addView(back, new LinearLayout.LayoutParams(dp(44), dp(52)));
        LinearLayout heading = column();
        TextView sub = text(attendanceView ? "Giờ vào/ra ghi nhận từ máy chủ" : "Lịch làm việc cá nhân", 12, 0xffdce1ff,
                false);
        sub.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        heading.addView(sub);
        TextView h = text(attendanceView ? "CHẤM CÔNG CỦA TÔI" : "LỊCH CỦA BẠN", 22, Color.WHITE, true);
        h.setAccessibilityHeading(true);
        h.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        heading.addView(h);
        hero.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton todayButton = calendarButton("Hôm nay", () -> {
            schedulePeriod.select(today());
            render();
        });
        todayButton.setTextColor(Color.WHITE);
        todayButton.setContentDescription("Đến lịch hôm nay");
        hero.addView(todayButton, new LinearLayout.LayoutParams(dp(76), dp(48)));
        body.addView(hero);
        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(dp(16), dp(12), dp(16), dp(12));
        String[] modeNames = attendanceView ? new String[] { "Công theo ngày", "Công theo tuần" }
                : new String[] { "Lịch theo ngày", "Lịch theo tuần" };
        for (int i = 0; i < 2; i++) {
            final boolean weekly = i == 1;
            MaterialButton b = calendarButton(modeNames[i], () -> {
                schedulePeriod.weekly(weekly);
                render();
            });
            b.setTextColor(schedulePeriod.weekly() == weekly ? Color.WHITE : CALENDAR_BLUE);
            b.setBackgroundTintList(android.content.res.ColorStateList
                    .valueOf(schedulePeriod.weekly() == weekly ? CALENDAR_BLUE : Color.WHITE));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i == 0)
                p.setMarginEnd(dp(12));
            b.setLayoutParams(p);
            b.setTag(tagPrefix + (weekly ? ".week" : ".day"));
            modes.addView(b);
        }
        body.addView(modes);
        LinearLayout period = new LinearLayout(this);
        period.setGravity(Gravity.CENTER_VERTICAL);
        period.setPadding(dp(16), dp(10), dp(12), dp(10));
        period.setBackgroundColor(CALENDAR_BLUE);
        LinearLayout labelBox = column();
        String caption = schedulePeriod.weekly()
                ? "Tuần " + schedulePeriod.week() + (schedulePeriod.contains(today()) ? " (Hiện tại)" : "")
                : schedulePeriod.anchor().equals(today()) ? "Hôm nay" : "Lịch theo ngày";
        TextView label = text(caption, 16, Color.WHITE, true);
        label.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        labelBox.addView(label);
        String range = schedulePeriod.first().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (schedulePeriod.weekly()
                        ? " – " + schedulePeriod.last().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : "");
        TextView dates = text(range, 12, 0xffe1e5ff, false);
        dates.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        labelBox.addView(dates);
        labelBox.setContentDescription("Chọn ngày lịch, " + range);
        labelBox.setOnClickListener(v -> pickDate(schedulePeriod.anchor(), date -> {
            schedulePeriod.select(date);
            render();
        }));
        labelBox.setFocusable(true);
        period.addView(labelBox, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout arrows = new LinearLayout(this);
        arrows.setGravity(Gravity.CENTER);
        arrows.setBackground(rounded(Color.WHITE, 30));
        MaterialButton previous = calendarButton("‹", () -> {
            schedulePeriod.move(-1);
            render();
        }), next = calendarButton("›", () -> {
            schedulePeriod.move(1);
            render();
        });
        previous.setTextSize(30);
        next.setTextSize(30);
        previous.setContentDescription(schedulePeriod.weekly() ? "Tuần trước" : "Ngày trước");
        next.setContentDescription(schedulePeriod.weekly() ? "Tuần sau" : "Ngày sau");
        previous.setTag(tagPrefix + ".previous");
        next.setTag(tagPrefix + ".next");
        arrows.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(44)));
        arrows.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        period.addView(arrows);
        body.addView(period);
        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(dp(12), dp(8), dp(12), 0);
        String[] names = { "Tất cả", "Ca thường", "OT" }, keys = { "all", "regular", "ot" };
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            MaterialButton b = calendarButton(names[i], () -> {
                if (attendanceView)
                    attendanceFilter = key;
                else
                    this.scheduleFilter = key;
                render();
            });
            b.setTextColor(scheduleFilter.equals(key) ? Color.WHITE : CALENDAR_BLUE);
            b.setBackgroundTintList(android.content.res.ColorStateList
                    .valueOf(scheduleFilter.equals(key) ? CALENDAR_BLUE : 0xffe8ecf8));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
            p.setMarginEnd(dp(6));
            filters.addView(b, p);
        }
        body.addView(filters);
        if (attendanceView) attendanceColorLegend(body);
        if (attendanceView) {
            LinearLayout summaryLink = column();
            summaryLink.setPadding(dp(16), 0, dp(16), 0);
            MaterialButton summaryButton = button(summaryLink, "Tổng công theo tháng / năm", false, () -> {
                section = "summary";
                render();
            });
            summaryButton.setTextColor(CALENDAR_BLUE);
            summaryButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffe8ecf8));
            summaryButton.setCornerRadius(dp(24));
            summaryButton.setStrokeWidth(dp(1));
            summaryButton.setStrokeColor(android.content.res.ColorStateList.valueOf(0xffcbd2ed));
            body.addView(summaryLink);
        }
        LinearLayout groups = column();
        body.addView(groups);
        TextView waiting = text(attendanceView ? "Đang tải công cá nhân…" : "Đang tải lịch cá nhân…", 14, MUTED, false);
        waiting.setPadding(dp(16), dp(20), dp(16), dp(20));
        groups.addView(waiting);
        int version = generation;
        LocalDate first = schedulePeriod.first(), last = schedulePeriod.last();
        String filter = scheduleFilter;
        loading.setVisibility(View.VISIBLE);
        ++pendingReads;
        String token = session.token();
        executor.execute(() -> {
            try {
                List<JSONObject> entries = new ArrayList<>();
                if (!filter.equals("ot"))
                    calendarPages(
                            "/api/attendance-workspace/records?mine=true&from=" + first.minusDays(1) + "&to=" + last,
                            "regular", token, entries);
                if (!filter.equals("regular"))
                    calendarPages(
                            "/api/overtime/workspace/calendar?mine=true&from=" + first.minusDays(1) + "&to=" + last,
                            "ot", token, entries);
                entries.sort(Comparator.comparing(e -> str(e, "calendarStart")));
                runOnUiThread(() -> {
                    if (isDestroyed() || version != generation)
                        return;
                    --pendingReads;
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    groups.removeAllViews();
                    for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                        List<JSONObject> daily = new ArrayList<>();
                        for (JSONObject entry : entries)
                            if (SchedulePeriod.overlaps(day, LocalDateTime.parse(str(entry, "calendarStart")),
                                    LocalDateTime.parse(str(entry, "calendarEnd"))))
                                daily.add(entry);
                        calendarDay(groups, day, daily);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || version != generation)
                        return;
                    --pendingReads;
                    loading.setVisibility(pendingReads > 0 ? View.VISIBLE : View.GONE);
                    groups.removeAllViews();
                    TextView error = text(HrmApi.message(e), 14, 0xffa32b32, false);
                    error.setPadding(dp(16), dp(20), dp(16), dp(16));
                    groups.addView(error);
                    MaterialButton retry = calendarButton("Tải lại lịch", this::render);
                    groups.addView(retry);
                    if (e instanceof HrmApi.ApiException && ((HrmApi.ApiException) e).status == 401) {
                        session.clearSession();
                        signOutLocal();
                    }
                });
            }
        });
    }

    private void calendarPages(String url, String kind, String token, List<JSONObject> entries) throws Exception {
        int page = 0, pages;
        do {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();
            JSONObject data = api.get(url + "&page=" + page, token);
            JSONArray items = data.optJSONArray("items");
            if (items == null)
                throw new org.json.JSONException("Missing calendar items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject source = items.getJSONObject(i),
                        row = kind.equals("ot") ? source.getJSONObject("row") : source,
                        o = kind.equals("ot") ? row.getJSONObject("ot") : row;
                String start = str(o, kind.equals("ot") ? "requestedStart" : "plannedStart"),
                        end = str(o, kind.equals("ot") ? "requestedEnd" : "plannedEnd");
                if (start.isEmpty() || end.isEmpty())
                    throw new org.json.JSONException("Missing calendar interval");
                entries.add(json("kind", kind, "source", source, "calendarStart", start, "calendarEnd", end));
            }
            pages = data.optInt("totalPages", 1);
            page++;
        } while (page < pages);
    }

    private void calendarDay(LinearLayout parent, LocalDate day, List<JSONObject> entries) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(18), dp(12), dp(6));
        TextView dot = text(day.equals(today()) ? "●" : "○", 20, day.equals(today()) ? 0xff2196f3 : 0xffa8c5de, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(rounded(0xffe0eef9, 24));
        row.addView(dot, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView date = text(day.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new Locale("vi", "VN"))), 15,
                INK, true);
        String holidayName=VietnameseHoliday.name(day);
        if(!holidayName.isEmpty()){date.setText(date.getText()+"\n"+holidayName);date.setTextColor(0xff8b0000);}
        date.setAccessibilityHeading(true);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, -2, 1);
        dateParams.setMarginStart(dp(10));
        row.addView(date, dateParams);
        TextView count = text(entries.size() + (section.equals("attendance") ? " ca" : " hoạt động"), 11, MUTED, false);
        count.setPadding(dp(8), dp(6), dp(8), dp(6));
        count.setBackground(rounded(0xffe0ebf5, 20));
        row.addView(count, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row);
        if (entries.isEmpty()) {
            TextView empty = text("Không có lịch làm việc.", 13, MUTED, false);
            empty.setPadding(dp(52), dp(6), dp(12), dp(10));
            parent.addView(empty);
            return;
        }
        for (JSONObject entry : entries)
            calendarEntry(parent, entry, day);
    }

    private void calendarEntry(LinearLayout parent, JSONObject entry, LocalDate day) {
        boolean ot = str(entry, "kind").equals("ot");
        JSONObject source = entry.optJSONObject("source"), row = ot ? source.optJSONObject("row") : source,
                o = ot ? row.optJSONObject("ot") : row;
        int accent = ot ? 0xff137957 : CALENDAR_BLUE;
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(rounded(Color.WHITE, 14));
        outer.setElevation(dp(1));
        outer.setClipToOutline(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(12), dp(6), dp(12), dp(12));
        outer.setLayoutParams(p);
        LinearLayout content = new LinearLayout(this);
        View stripe = new View(this);
        stripe.setBackgroundColor(accent);
        content.addView(stripe, new LinearLayout.LayoutParams(dp(4), -1));
        LinearLayout box = column();
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        TextView type = text(
                ot ? "●  Tăng ca (OT)" : section.equals("attendance") ? "●  Công theo ca" : "●  Lịch làm việc", 12,
                accent, true);
        type.setPadding(dp(10), dp(6), dp(10), dp(6));
        type.setBackground(rounded(ot ? 0xffe7f5ee : 0xffededfa, 20));
        type.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        box.addView(type);
        String title = ot ? "Tăng ca · " + str(o, "requestCode") : str(row, "shift");
        TextView name = text(title, 17, accent, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(12);
        nameParams.bottomMargin = dp(10);
        name.setLayoutParams(nameParams);
        box.addView(name);
        LocalDateTime start = LocalDateTime.parse(str(entry, "calendarStart")),
                end = LocalDateTime.parse(str(entry, "calendarEnd"));
        String timing = start.format(DateTimeFormatter.ofPattern("HH:mm")) + " – "
                + end.format(DateTimeFormatter.ofPattern("HH:mm")) + " · "
                + start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (!start.toLocalDate().equals(end.toLocalDate()))
            timing += " → " + end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        box.addView(text("◷  " + timing, 13, INK, false));
        if (start.toLocalDate().isBefore(day))
            box.addView(text("Ca qua đêm từ ngày trước", 12, MUTED, false));
        String department = str(row, "department");
        if (!department.isEmpty())
            box.addView(text("▦  Phòng ban: " + department, 13, MUTED, false));
        if (!ot)
            box.addView(text("◷  Nghỉ theo lịch: " + row.optInt("plannedBreakMinutes") + " phút", 13, MUTED, false));
        else
            box.addView(text("Đã duyệt · "
                    + (str(o, "actualEnd").isEmpty() ? str(o, "actualStart").isEmpty() ? "Chưa check-in" : "Đã check-in"
                            : "Đã check-out"),
                    12, accent, true));
        if (section.equals("attendance"))
            attendanceFacts(box, row, o, ot);
        Runnable detail = () -> {
            if (writing)
                return;
            if (ot)
                fetch("/api/overtime/workspace/requests/" + o.optLong("overtimeId"), this::otDetail);
            else
                fetch("/api/attendance-workspace/schedules/" + row.optLong("scheduleId") + "?mine=true",
                        this::attendanceDetail);
        };
        outer.setClickable(true);
        outer.setFocusable(true);
        outer.setContentDescription(title + ", " + timing + ", chạm để xem chi tiết");
        outer.setOnClickListener(v -> detail.run());
        box.addView(text("Chạm để xem chi tiết", 11, MUTED, false));
        if (!ot && row.optBoolean("canCheckIn"))
            button(box, "Check-in", true,
                    () -> write("/api/attendance-workspace/schedules/" + row.optLong("scheduleId") + "/check-in",
                            "POST", new JSONObject(), "Check-in theo giờ máy chủ?", null));
        if (!ot && row.optBoolean("canCheckOut"))
            button(box, "Check-out", true,
                    () -> write("/api/attendance-workspace/schedules/" + row.optLong("scheduleId") + "/check-out",
                            "POST", new JSONObject(), "Check-out theo giờ máy chủ?", null));
        if (ot && source.optBoolean("canCheckIn"))
            button(box, "Check-in OT", true, () -> write(
                    "/api/overtime/workspace/calendar/" + o.optLong("overtimeId") + "/check-in", "POST",
                    json("expectedVersion", str(row, "version"), "breaks", new JSONArray(), "noBreaksConfirmed", false),
                    "Check-in OT theo giờ máy chủ?", null));
        if (ot && source.optBoolean("canCheckOut"))
            button(box, "Check-out OT", true, () -> otCheckout(row));
        if(section.equals("attendance")&&!ot&&"DA_XAC_NHAN".equals(str(row,"verificationStatus"))) {
            button(box,"Yêu cầu điều chỉnh / bổ sung",false,()->{}).setEnabled(false);
            box.addView(text("Công đã được xác nhận",12,MUTED,false));
        }
        if (section.equals("attendance") && !ot) addAttendanceProposalAction(box, row);
        if (section.equals("attendance") && ot && str(o, "actualEnd").isEmpty())
            button(box, "Bổ sung công OT", false, () -> supplementCandidates(o.optLong("overtimeId")));
        outer.addView(content);
        parent.addView(outer);
    }

    private String clockReviewState(JSONObject row,boolean checkIn){
        String side=checkIn?"check-in":"check-out";
        if(str(row,checkIn?"actualStart":"actualEnd").isEmpty())return "Chưa "+side;
        if("DA_XAC_NHAN".equals(str(row,"verificationStatus")))return "Đã xác nhận "+side+" trong công";
        return row.optBoolean(checkIn?"checkInReviewed":"checkOutReviewed")?"Đã đối soát "+side:"Đã "+side+" — chờ đối soát";
    }
    private void clockReviewFacts(LinearLayout box,JSONObject row){
        for(boolean checkIn:new boolean[]{true,false}){
            boolean recorded=!str(row,checkIn?"actualStart":"actualEnd").isEmpty();
            boolean reviewed=row.optBoolean(checkIn?"checkInReviewed":"checkOutReviewed");
            boolean confirmed="DA_XAC_NHAN".equals(str(row,"verificationStatus"));
            badge(box,clockReviewState(row,checkIn),(reviewed||confirmed)?"DA_DOI_SOAT":recorded?"CHO_DOI_SOAT":"CHUA_GHI_NHAN");
            if(reviewed)box.addView(text(str(row,checkIn?"checkInReviewer":"checkOutReviewer")+" · "+utc(str(row,checkIn?"checkInReviewedAt":"checkOutReviewedAt")),12,MUTED,false));
        }
    }

    private void clockTimingFacts(LinearLayout box,String plannedStart,String plannedEnd,String actualStart,String actualEnd){
        String in=ClockTiming.describe(plannedStart,actualStart,true),out=ClockTiming.describe(plannedEnd,actualEnd,false);
        if(!in.isEmpty())box.addView(text(in,13,INK,true));
        if(!out.isEmpty())box.addView(text(out,13,INK,true));
    }

    private void supplementRequestBadge(LinearLayout box, JSONObject row) {
        String status = str(row, "supplementRequestStatus");
        String note;
        switch (status) {
            case "CHO_XU_LY": note = "Đã gửi yêu cầu bổ sung công · Chờ xử lý"; break;
            case "DANG_XU_LY": note = "Yêu cầu bổ sung công · Đã tiếp nhận"; break;
            case "DA_CHAP_NHAN": note = "Yêu cầu bổ sung công · Đã chấp nhận"; break;
            case "TU_CHOI": note = "Yêu cầu bổ sung công · Đã từ chối"; break;
            default: return;
        }
        if (!note.isEmpty()) {
            badge(box, note, status);
        }
    }

    private void attendanceFacts(LinearLayout box, JSONObject row, JSONObject actual, boolean ot) {
        clockTimingFacts(box,str(ot?actual:row,ot?"requestedStart":"plannedStart"),str(ot?actual:row,ot?"requestedEnd":"plannedEnd"),str(actual,"actualStart"),str(actual,"actualEnd"));
        line(box, "Giờ vào thực tế", str(actual,"actualStart").isEmpty()?"Chưa check-in":time(str(actual,"actualStart")));
        line(box, "Giờ ra thực tế", str(actual,"actualEnd").isEmpty()?"Chưa check-out":time(str(actual,"actualEnd")));
        if (!ot) {
            String status = str(row, "verificationStatus");
            badge(box, "DA_XAC_NHAN".equals(status)?"Công đã được xác nhận":"Công chưa xác nhận toàn bộ",status.isEmpty()?"CHO":status);
            clockReviewFacts(box,row);
            supplementRequestBadge(box, row);
            String result = str(row, "attendanceResult");
            if (!result.isEmpty())
                badge(box, label(result), result);
            JSONArray anomalies = row.optJSONArray("anomalies");
            if (anomalies != null && anomalies.length() > 0)
                badge(box,
                        anomalies.toString().replace("[", "").replace("]", "").replace("\"", "")
                                .replace("THIEU_VAO", "Thiếu giờ vào").replace("THIEU_RA", "Thiếu giờ ra")
                                .replace("DI_MUON", "Đi muộn").replace("VE_SOM", "Về sớm"),
                        "THIEU");
        }
    }

    private LocalDate summaryAnchor = today().withDayOfMonth(1);
    private boolean summaryYear = false;

    private String hours(long minutes) {
        return (minutes / 60) + " giờ " + (minutes % 60) + " phút";
    }

    private void attendanceSummary() {
        title("Tổng công của tôi", "Theo tháng hoặc năm · Công thường đã xác nhận");
        LinearLayout controls = card(null);
        button(controls, "‹ Quay lại chấm công", false, () -> {
            section = "attendance";
            render();
        });
        LinearLayout modes = new LinearLayout(this);
        for (boolean year : new boolean[] { false, true }) {
            MaterialButton b = calendarButton(year ? "Theo năm" : "Theo tháng", () -> {
                summaryYear = year;
                render();
            });
            b.setTextColor(summaryYear == year ? Color.WHITE : CALENDAR_BLUE);
            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(summaryYear == year ? CALENDAR_BLUE : 0xffe8ecf8));
            modes.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        controls.addView(modes);
        button(controls,
                summaryYear ? "Năm " + summaryAnchor.getYear()
                        : "Tháng " + summaryAnchor.format(DateTimeFormatter.ofPattern("MM/yyyy")),
                false, () -> pickDate(summaryAnchor, d -> {
                    summaryAnchor = d.withDayOfMonth(1);
                    render();
                }));
        LinearLayout arrows = new LinearLayout(this);
        MaterialButton prev = calendarButton("‹ Kỳ trước", () -> {
            summaryAnchor = summaryYear ? summaryAnchor.minusYears(1) : summaryAnchor.minusMonths(1);
            render();
        }), next = calendarButton("Kỳ sau ›", () -> {
            summaryAnchor = summaryYear ? summaryAnchor.plusYears(1) : summaryAnchor.plusMonths(1);
            render();
        });
        arrows.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1));
        arrows.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(arrows);
        LocalDate first = summaryYear ? summaryAnchor.withDayOfYear(1) : summaryAnchor,
                last = summaryYear ? first.plusYears(1).minusDays(1) : first.plusMonths(1).minusDays(1);
        final boolean yearly = summaryYear;
        body.addView(text(
                "Chỉ cộng công đã xác nhận, chưa vô hiệu. Lượt đi làm không phải ngày công quy đổi. OT theo dõi riêng, chưa cộng vào tổng này. Công thiếu dữ liệu hoặc ca qua đêm chưa đủ chi tiết nghỉ được đánh dấu cần đối soát.",
                13, MUTED, false));
        fetch("/api/me/attendance-summary?from=" + first + "&to=" + last, data -> {
            JSONArray days = data.getJSONArray("days");
            long present = 0, absent = 0;
            for (int i = 0; i < days.length(); i++) {
                present += days.getJSONObject(i).optLong("presentRecords");
                absent += days.getJSONObject(i).optLong("absentRecords");
            }
            LinearLayout total = card("Tổng kỳ " + first.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " – "
                    + last.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            line(total, "Giờ công thuần", hours(data.getLong("netMinutes")));
            line(total, "Lượt đi làm / vắng", present + " / " + absent);
            line(total, "Bản ghi cần đối soát", String.valueOf(data.getLong("unresolvedRecords")));
            if (days.length() == 0) {
                card("Chưa có công đã xác nhận").addView(text("Không có dữ liệu trong kỳ này.", 14, MUTED, false));
                return;
            }
            if (yearly) {
                for (int month = 1; month <= 12; month++) {
                    long minutes = 0, p = 0, a = 0, u = 0;
                    for (int i = 0; i < days.length(); i++) {
                        JSONObject d = days.getJSONObject(i);
                        if (LocalDate.parse(d.getString("workDate")).getMonthValue() == month) {
                            minutes += d.getLong("netMinutes");
                            p += d.getLong("presentRecords");
                            a += d.getLong("absentRecords");
                            u += d.getLong("unresolvedRecords");
                        }
                    }
                    LinearLayout c = card("Tháng " + month);
                    line(c, "Giờ công thuần", hours(minutes));
                    line(c, "Đi làm / vắng", p + " / " + a);
                    if (u > 0)
                        badge(c, u + " bản ghi cần đối soát", "THIEU");
                    final int selected = month;
                    button(c, "Xem tháng", false, () -> {
                        summaryAnchor = LocalDate.of(first.getYear(), selected, 1);
                        summaryYear = false;
                        render();
                    });
                }
            } else
                for (int i = 0; i < days.length(); i++) {
                    JSONObject d = days.getJSONObject(i);
                    LinearLayout c = card(LocalDate.parse(d.getString("workDate"))
                            .format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new Locale("vi", "VN"))));
                    line(c, "Giờ công thuần", hours(d.getLong("netMinutes")));
                    line(c, "Đi làm / vắng", d.getLong("presentRecords") + " / " + d.getLong("absentRecords"));
                    if (d.getLong("unresolvedRecords") > 0)
                        badge(c, d.getLong("unresolvedRecords") + " bản ghi cần đối soát", "THIEU");
                }
        });
    }

    private void attendance() {
        schedule();
    }

    private void attendanceCard(JSONObject r) {
        LinearLayout box = card(str(r, "shift"));
        line(box, "Ngày ca", str(r, "workDate"));
        line(box, "Dự kiến", time(str(r, "plannedStart")) + " → " + time(str(r, "plannedEnd")));
        badge(box, "DA_XAC_NHAN".equals(str(r,"verificationStatus")) ? "Công đã được xác nhận" : "Công chưa xác nhận toàn bộ", str(r,"verificationStatus").isEmpty()?"CHO":str(r,"verificationStatus"));
        line(box,"Giờ check-in",str(r,"actualStart").isEmpty()?"Chưa check-in":time(str(r,"actualStart")));
        line(box,"Giờ check-out",str(r,"actualEnd").isEmpty()?"Chưa check-out":time(str(r,"actualEnd")));
        clockReviewFacts(box,r);
        clockTimingFacts(box,str(r,"plannedStart"),str(r,"plannedEnd"),str(r,"actualStart"),str(r,"actualEnd"));
        JSONArray anomalies = r.optJSONArray("anomalies");
        if (anomalies != null && anomalies.length() > 0)
            badge(box,
                    anomalies.toString().replace("[", "").replace("]", "").replace("\"", "")
                            .replace("THIEU_VAO", "Thiếu giờ vào").replace("THIEU_RA", "Thiếu giờ ra")
                            .replace("DI_MUON", "Đi muộn").replace("VE_SOM", "Về sớm"),
                    "THIEU");
        long id = r.optLong("scheduleId");
        button(box, "Xem chi tiết", false,
                () -> fetch("/api/attendance-workspace/schedules/" + id + "?mine=true", this::attendanceDetail));
        if (r.optBoolean("canCheckIn"))
            button(box, "Check-in", true, () -> write("/api/attendance-workspace/schedules/" + id + "/check-in", "POST",
                    new JSONObject(), "Check-in ca này bằng giờ máy chủ?", null));
        if (r.optBoolean("canCheckOut"))
            button(box, "Check-out", true, () -> write("/api/attendance-workspace/schedules/" + id + "/check-out",
                    "POST", new JSONObject(), "Check-out ca này bằng giờ máy chủ?", null));
        if("DA_XAC_NHAN".equals(str(r,"verificationStatus"))) {
            button(box,"Yêu cầu điều chỉnh / bổ sung",false,()->{}).setEnabled(false);
            box.addView(text("Công đã được xác nhận",12,MUTED,false));
        }
        addAttendanceProposalAction(box, r);
    }

    private void attendanceDetail(JSONObject detail) throws Exception {
        JSONObject r = detail.getJSONObject("row");
        StringBuilder info = new StringBuilder();
        info.append("Ngày ca: ").append(str(r, "workDate")).append("\nDự kiến: ").append(time(str(r, "plannedStart")))
                .append(" → ").append(time(str(r, "plannedEnd"))).append("\nVào: ").append(time(str(r, "actualStart")))
                .append("\nRa: ").append(time(str(r, "actualEnd"))).append("\nNghỉ thực tế: ")
                .append(r.optInt("actualBreakMinutes")).append(" phút\nNgười nhập: ").append(str(r, "enteredByLabel"))
                .append("\nNgười xác nhận: ").append(str(r, "confirmedByLabel"));
        if("DA_XAC_NHAN".equals(str(r,"verificationStatus")))info.append("\nCông đã được xác nhận");
        info.append("\nCheck-in: ").append(clockReviewState(r,true)).append("\nCheck-out: ").append(clockReviewState(r,false));
        if(r.optBoolean("checkInReviewed"))info.append("\nĐối soát giờ vào: ").append(str(r,"checkInReviewer")).append(" · ").append(utc(str(r,"checkInReviewedAt")));
        if(r.optBoolean("checkOutReviewed"))info.append("\nĐối soát giờ ra: ").append(str(r,"checkOutReviewer")).append(" · ").append(utc(str(r,"checkOutReviewedAt")));
        info.append("\n").append(ClockTiming.describe(str(r,"plannedStart"),str(r,"actualStart"),true)).append("\n").append(ClockTiming.describe(str(r,"plannedEnd"),str(r,"actualEnd"),false));
        JSONArray rests = detail.optJSONArray("breaks");
        if (rests != null)
            for (int i = 0; i < rests.length(); i++) {
                JSONObject b = rests.getJSONObject(i);
                info.append("\nNghỉ: ").append(time(str(b, "start"))).append(" → ").append(time(str(b, "end")));
            }
        JSONArray linked = detail.optJSONArray("requests");
        if(linked!=null)for(int i=0;i<linked.length();i++){
            JSONObject request=linked.getJSONObject(i);
            info.append("\nYêu cầu: ").append(str(request,"requestCode")).append(" · ").append(label(str(request,"status")))
                .append("\nTiếp nhận: ").append(str(request,"receiver")).append(" · ").append(utc(str(request,"receivedAt")))
                .append("\nXử lý: ").append(str(request,"processor")).append(" · ").append(utc(str(request,"processedAt")));
        }
        MaterialAlertDialogBuilder details = new MaterialAlertDialogBuilder(this).setTitle(str(r, "shift")).setMessage(info).setPositiveButton("Đóng", null);
        if (r.optBoolean("canRequest") && !"DA_XAC_NHAN".equals(str(r,"verificationStatus"))) {
            details.setNeutralButton(attendanceProposalLabel(r), (dialog, which) -> attendanceProposal(r));
        } else if (r.optBoolean("checkoutSupplementTooEarly")) {
            info.append("\nCa còn trong giờ làm việc. Hãy check-out trực tiếp khi kết thúc làm việc.");
            details.setMessage(info.toString());
        }
        details.show();
    }

    private void otCalendar() {
        fetch("/api/overtime/workspace/calendar?mine=true&" + query(), data -> {
            JSONArray items = data.optJSONArray("items");
            if (items == null || items.length() == 0)
                card("Chưa có lịch OT").addView(text("OT được duyệt sẽ xuất hiện tại đây.", 14, MUTED, false));
            else
                for (int i = 0; i < items.length(); i++) {
                    JSONObject slot = items.getJSONObject(i), row = slot.getJSONObject("row"),
                            o = row.getJSONObject("ot");
                    LinearLayout box = card(str(o, "requestCode"));
                    badge(box, "Tăng ca · Đã duyệt", "DA_DUYET");
                    line(box, "Lịch OT", time(str(o, "requestedStart")) + " → " + time(str(o, "requestedEnd")));
                    line(box, "Thực tế", time(str(o, "actualStart")) + " → " + time(str(o, "actualEnd")));
                    if (slot.optBoolean("canCheckIn"))
                        button(box, "Check-in OT", true,
                                () -> write("/api/overtime/workspace/calendar/" + o.optLong("overtimeId") + "/check-in",
                                        "POST", json("expectedVersion", str(row, "version"), "breaks", new JSONArray(),
                                                "noBreaksConfirmed", false),
                                        "Check-in OT theo giờ máy chủ?", null));
                    if (slot.optBoolean("canCheckOut"))
                        button(box, "Check-out OT", true, () -> otCheckout(row));
                    button(box, "Chi tiết đơn OT", false,
                            () -> fetch("/api/overtime/workspace/requests/" + o.optLong("overtimeId"), this::otDetail));
                    if (str(o, "actualEnd").isEmpty())
                        button(box, "Bổ sung công OT", false, () -> supplementCandidates(o.optLong("overtimeId")));
                }
            pages(data);
        });
    }


    private void ownResignations() {
        LinearLayout create=card("Đơn xin nghỉ việc của tôi");
        create.addView(text("Gửi đề nghị để trưởng phòng và HR/ADMIN xử lý theo thẩm quyền. Nộp hoặc duyệt đơn chưa đồng nghĩa nghỉ việc ngay; cần hoàn tất bàn giao và đến ngày hiệu lực.",13,MUTED,false));
        button(create,"Nộp đơn xin nghỉ việc",true,this::resignationForm);
        fetch("/employee-requests/mine?type=NGHI_VIEC&page="+page+"&size=10",data->{
            JSONArray rows=data.optJSONArray("content");
            if(rows==null||rows.length()==0)card("Chưa có đơn nghỉ việc").addView(text("Đơn đã gửi và kết quả xử lý sẽ hiển thị tại đây.",13,MUTED,false));
            else for(int i=0;i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);LinearLayout box=card(str(r,"requestCode"));
                badge(box,!str(r,"withdrawnAt").isEmpty()?"Đã rút đơn":label(str(r,"status")),str(r,"status"));
                line(box,"Ngày làm cuối đề nghị",str(r,"requestedLastWorkingDate"));
                line(box,"Ngày làm cuối được duyệt",str(r,"approvedLastWorkingDate"));
                line(box,"Ngày gửi",utc(str(r,"submittedAt")));line(box,"Lý do",str(r,"reason"));
                button(box,"Chi tiết và lịch sử",false,()->fetch("/employee-requests/"+r.optLong("requestId"),detail->{
                    StringBuilder message=new StringBuilder("Trạng thái: ").append(!str(detail,"withdrawnAt").isEmpty()?"Đã rút đơn":label(str(detail,"status")))
                        .append("\nNgày làm cuối đề nghị: ").append(str(detail,"requestedLastWorkingDate"))
                        .append("\nNgày làm cuối được duyệt: ").append(str(detail,"approvedLastWorkingDate"))
                        .append("\nNgày gửi: ").append(utc(str(detail,"submittedAt")))
                        .append("\nNgày xử lý: ").append(utc(str(detail,"processedAt")))
                        .append("\nLý do: ").append(str(detail,"reason"))
                        .append("\nLý do từ chối: ").append(str(detail,"rejectionReason"))
                        .append("\nLưu ý báo trước: ").append(str(detail,"noticeWarning"));
                    JSONArray history=detail.optJSONArray("history");
                    if(history!=null)for(int n=0;n<history.length();n++){JSONObject h=history.getJSONObject(n);message.append("\n\n").append(utc(str(h,"occurredAt"))).append(" · ").append(str(h,"actor")).append("\n").append(resignationAction(str(h,"action"))).append("\n").append(str(h,"reason"));}
                    new MaterialAlertDialogBuilder(this).setTitle(str(detail,"requestCode")).setMessage(message).setPositiveButton("Đóng",null).show();
                }));
                if("CHO_DUYET".equals(str(r,"status"))&&str(r,"withdrawnAt").isEmpty())button(box,"Rút đơn",false,()->{
                    LinearLayout form=form();TextInputEditText reason=input(form,"Lý do rút đơn *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    dialog("Rút đơn xin nghỉ việc",form,"Tiếp tục",d->{if(value(reason).isEmpty()||value(reason).length()>1000){reason.setError("Nhập lý do tối đa 1000 ký tự");reason.requestFocus();return;}write("/employee-requests/"+r.optLong("requestId")+"/withdraw","POST",json("reason",value(reason)),"Xác nhận rút đơn xin nghỉ việc?",d);});
                });
            }
            pages(data);
        });
    }
    private String resignationAction(String action){
        switch(action){case "RESIGNATION_REQUEST_CREATE":return "Nộp đơn nghỉ việc";case "REQUEST_HEAD_APPROVE":return "Duyệt cấp 1";case "REQUEST_HEAD_REJECT":return "Từ chối cấp 1";case "REQUEST_APPROVE":return "Duyệt cuối";case "REQUEST_REJECT":return "Từ chối đơn";case "REQUEST_WITHDRAW":return "Rút đơn";case "RESIGNATION_EXECUTE":return "Áp dụng nghỉ việc";default:return action;}
    }
    private void resignationForm(){
        LinearLayout box=form();box.addView(text("Chọn ngày làm việc cuối cùng đề nghị. Hệ thống kiểm tra thời hạn báo trước và các nghĩa vụ bàn giao khi duyệt.",13,MUTED,false));
        final LocalDate[] last={today().plusDays(30)};
        TextInputEditText date=input(box,"Ngày làm việc cuối đề nghị *",last[0].format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),InputType.TYPE_NULL);
        date.setFocusable(false);date.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);
        date.setOnClickListener(v->pickDate(last[0],chosen->{last[0]=chosen;date.setText(chosen.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));}));
        TextInputEditText reason=input(box,"Lý do xin nghỉ việc *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog("Nộp đơn xin nghỉ việc",box,"Gửi đơn",d->{
            if(last[0].isBefore(today())){date.setError("Ngày làm cuối không được ở quá khứ");date.requestFocus();return;}
            if(value(reason).isEmpty()||value(reason).length()>1000){reason.setError("Nhập lý do tối đa 1000 ký tự");reason.requestFocus();return;}
            write("/employee-requests/resignation","POST",json("requestedLastWorkingDate",last[0].toString(),"reason",value(reason)),"Xác nhận gửi đơn xin nghỉ việc với ngày làm cuối "+last[0].format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))+"?",d);
        });
    }

    private void ownLeaveRequests() {
        LinearLayout create=card("Nghỉ phép của tôi");
        create.addView(text("Nghỉ phép năm trừ quỹ giờ khi được duyệt. Nghỉ ốm và thai sản theo chính sách riêng, không trừ phép năm. Đơn được chuyển theo quy trình duyệt của trung tâm.",12,MUTED,false));
        button(create,"Nộp đơn nghỉ phép",true,()->fetch("/api/me/leave-options",options->leaveForm(options,false)));
        button(create,"Bổ sung phép",false,()->fetch("/api/me/leave-options",options->leaveForm(options,true)));
        fetch("/employee-requests/mine?type=NGHI_PHEP&size=100", data->{
            JSONArray rows=data.optJSONArray("content");
            if(rows==null||rows.length()==0){card("Chưa có đơn nghỉ phép").addView(text("Các đơn mới sẽ xuất hiện ở đây sau khi gửi thành công.",13,MUTED,false));return;}
            for(int i=0;i<rows.length();i++){
                JSONObject request=rows.getJSONObject(i);LinearLayout box=card(str(request,"requestCode"));
                badge(box,str(request,"withdrawnAt").isEmpty()?label(str(request,"status")):"Đã rút / hủy",str(request,"status"));
                line(box,"Thời gian nghỉ",time(str(request,"leaveStart"))+" → "+time(str(request,"leaveEnd")));
                line(box,"Lý do",str(request,"reason"));
                if(request.optBoolean("canCancelLeave"))button(box,"CHO_DUYET".equals(str(request,"status"))?"Rút đơn nghỉ phép":"Hủy đơn nghỉ phép",false,()->cancelLeaveForm(request));
                else if(!str(request,"cancelLeaveBlocker").isEmpty())box.addView(text(str(request,"cancelLeaveBlocker"),12,MUTED,false));
                if(!str(request,"withdrawnAt").isEmpty())line(box,"Hủy đơn",utc(str(request,"withdrawnAt"))+" · "+str(request,"withdrawnBy")+" · "+str(request,"withdrawalReason"));
                button(box,"Chi tiết và kết quả",false,()->fetch("/employee-requests/"+request.optLong("requestId"),detail->{
                    String value="Trạng thái: "+label(str(detail,"status"))+"\nThời gian: "+time(str(detail,"leaveStart"))+" → "+time(str(detail,"leaveEnd"))+"\nLý do: "+str(detail,"reason")+"\nNgày gửi: "+utc(str(detail,"submittedAt"))+"\nNgười xử lý: "+str(detail,"processedBy")+"\nLúc xử lý: "+utc(str(detail,"processedAt"))+"\nLý do từ chối: "+str(detail,"rejectionReason");
                    if(!str(detail,"withdrawnAt").isEmpty())value+="\nNgười hủy: "+str(detail,"withdrawnBy")+"\nLúc hủy: "+utc(str(detail,"withdrawnAt"))+"\nLý do hủy: "+str(detail,"withdrawalReason");
                    new MaterialAlertDialogBuilder(this).setTitle(str(detail,"requestCode")).setMessage(value).setPositiveButton("Đóng",null).show();
                }));
            }
        });
    }
    private void cancelLeaveForm(JSONObject request){
        boolean pending = "CHO_DUYET".equals(str(request,"status"));
        String action = pending ? "Rút đơn nghỉ phép" : "Hủy đơn nghỉ phép";
        LinearLayout box=form();
        box.addView(text(pending ? "Bạn có thể rút đơn khi cấp trên chưa xử lý."
                : "Đơn đã duyệt được hoàn quỹ phép nếu hủy hợp lệ.",13,INK,false));
        TextInputEditText reason=input(box,"Lý do hủy *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog(action,box,"Tiếp tục",dialog->{if(value(reason).isEmpty()||value(reason).length()>1000){reason.setError("Nhập lý do tối đa 1000 ký tự");reason.requestFocus();return;}write("/employee-requests/"+request.optLong("requestId")+(pending ? "/withdraw" : "/cancel-leave"),"POST",json("reason",value(reason)),"Xác nhận "+action.toLowerCase(java.util.Locale.ROOT)+"?",dialog);});
    }
    private void leaveForm(JSONObject options, boolean retroactive) throws Exception {
        JSONArray types=options.optJSONArray("types");if(types==null||types.length()==0){toast("Chưa có loại nghỉ hoạt động. Liên hệ ADMIN.");return;}
        LinearLayout box=form();List<String> labels=new ArrayList<>();for(int i=0;i<types.length();i++)labels.add(types.getJSONObject(i).getString("typeName"));
        box.addView(text("Loại nghỉ",13,INK,true));Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));box.addView(type);
        LocalDate initialDay = retroactive ? today().minusDays(1) : today();
        box.addView(text(retroactive ? "Chọn khoảng nghỉ đã qua, trong hạn nộp bù của loại nghỉ." : "Chọn ngày nghỉ từ hôm nay trở đi.",12,MUTED,false));
        DateField start=new DateField(box,"Bắt đầu nghỉ *",initialDay.atTime(8,0).toString(),false),end=new DateField(box,"Kết thúc nghỉ * (không gồm thời điểm này)",initialDay.atTime(17,0).toString(),false);
        TextView policy=text("",12,MUTED,false);box.addView(policy);
        TextInputEditText reason=input(box,"Lý do *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE),evidence=input(box,"Minh chứng đính kèm (JPG, PNG, PDF)","",InputType.TYPE_CLASS_TEXT);
        evidence.setKeyListener(null);leaveEvidenceTarget=evidence;button(box,"Chọn giấy khám bệnh / minh chứng",false,()->{if(!evidenceUploading)leaveEvidencePicker.launch("*/*");});
        Runnable update=()->{JSONObject t=types.optJSONObject(type.getSelectedItemPosition());if(t==null)return;String code=str(t,"typeCode");boolean maternity=code.equals("MATERNITY")||code.equals("THAI_SAN");policy.setText(maternity?"Chính sách trung tâm: nam tối đa "+options.optInt("maleDays",14)+" ngày lịch, nữ tối đa "+options.optInt("femaleMonths",6)+" tháng mỗi đơn. Cần minh chứng.":t.optBoolean("evidenceRequired")?"Loại nghỉ này cần tham chiếu giấy tờ minh chứng.":"Phép năm tính theo giờ làm việc được phân công, trừ quỹ khi được duyệt.");if(maternity){try{LocalDateTime from=LocalDateTime.parse(start.value);end.value=(str(options,"gender").equals("MALE")?from.plusDays(options.optInt("maleDays",14)):from.plusMonths(options.optInt("femaleMonths",6))).toString();end.edit.setText(time(end.value));}catch(Exception ignored){}}};
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){update.run();}});
        start.edit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable e){update.run();}});
        dialog(retroactive?"Bổ sung phép":"Nộp đơn nghỉ phép",box,"Gửi đơn",d->{JSONObject t=types.optJSONObject(type.getSelectedItemPosition());if(t==null)return;
            if(start.value.isEmpty()||end.value.isEmpty()||end.value.compareTo(start.value)<=0){end.edit.setError("Giờ kết thúc phải sau bắt đầu");end.edit.requestFocus();return;}
            if(value(reason).isEmpty()||value(reason).length()>1000){reason.setError("Nhập lý do tối đa 1000 ký tự");reason.requestFocus();return;}
            if(evidenceUploading){toast("Đợi tải minh chứng xong");return;}if(t.optBoolean("evidenceRequired")&&value(evidence).isEmpty()){evidence.setError("Cần đính kèm minh chứng");evidence.requestFocus();return;}
            boolean pastDate = LocalDateTime.parse(start.value).toLocalDate().isBefore(today());
            if (pastDate != retroactive) {
                start.edit.setError(retroactive ? "Chọn ngày trước hôm nay" : "Ngày đã qua. Hãy chọn Bổ sung phép.");
                start.edit.requestFocus();
                return;
            }
            write("/employee-requests/leave","POST",json("leaveTypeId",t.optLong("leaveTypeId"),"leaveStart",start.value,"leaveEnd",end.value,"reason",value(reason),"evidenceReference",value(evidence),"retroactive",retroactive),retroactive?"Gửi đơn bổ sung phép để xét duyệt?":"Gửi đơn nghỉ phép để xét duyệt?",d);
        });
    }

    private void requests() {
        title("Đơn của tôi", "Theo dõi đề nghị cá nhân và kết quả xử lý.");
        tabs(new String[] { "Điều chỉnh công", "Đơn OT", "Bổ sung công OT", "Nghỉ phép", "Nghỉ việc" },
                new String[] { "regular", "ot", "supplement", "leave", "resignation" });
        if(category.equals("resignation")){ownResignations();return;}
        if(category.equals("leave")){ownLeaveRequests();return;}
        range();
        if (category.equals("ot")) {
            ownOt();
            return;
        }
        if (category.equals("supplement")) {
            ownSupplements();
            return;
        }
        LinearLayout create = card("Yêu cầu công cá nhân");
        button(create, "Tạo yêu cầu điều chỉnh công", true, this::attendanceCandidates);
        fetch("/api/attendance-workspace/requests?mine=true&" + query(), data -> {
            JSONArray rows = data.optJSONArray("items");
            if (rows == null || rows.length() == 0)
                card("Chưa có yêu cầu")
                        .addView(text("Bạn chưa có yêu cầu phù hợp trong khoảng ngày này.", 14, MUTED, false));
            else
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    LinearLayout box = card(str(r, "requestCode"));
                    badge(box, label(str(r, "status")), str(r, "status"));
                    line(box, "Loại yêu cầu", label(str(r, "requestType")));
                    line(box, "Ngày ca", str(r, "workDate"));
                    line(box, "Lý do", str(r, "reason"));
                    button(box, "Chi tiết và kết quả", false,
                            () -> fetch("/api/attendance-workspace/requests/" + r.optLong("requestId") + "?mine=true",
                                    d -> {
                                        JSONObject q = d.getJSONObject("request");
                                        new MaterialAlertDialogBuilder(this).setTitle(str(q, "requestCode"))
                                                .setMessage("Trạng thái: " + label(str(q, "status")) + "\nGiờ đề nghị: "
                                                        + time(str(q, "proposedStart")) + " → "
                                                        + time(str(q, "proposedEnd")) + "\nLý do: " + str(q, "reason")
                                                        + "\nNgày gửi: " + utc(str(q, "submittedAt")) + "\nNgười tiếp nhận: " + str(q, "receiver") + "\nGiờ tiếp nhận: " + utc(str(q, "receivedAt")) + "\nNgười xử lý: "
                                                        + str(q, "processor") + "\nGiờ xử lý: " + utc(str(q, "processedAt")) + "\nGiờ được duyệt: " + time(str(q, "acceptedStart")) + " → " + time(str(q, "acceptedEnd")) + "\nLý do điều chỉnh giờ: " + str(q, "adjustmentReason") + "\nKết quả: "
                                                        + str(q, "decisionReason"))
                                                .setPositiveButton("Đóng", null).show();
                                    }));
                }
            pages(data);
        });
    }

    private void attendanceCandidatePages(String url, int candidatePage, JSONArray collected, Reply reply) {
        fetch(url + "&page=" + candidatePage, data -> {
            JSONArray rows = data.optJSONArray("items");
            if (rows == null) throw new org.json.JSONException("Thiếu danh sách công từ máy chủ.");
            for (int i = 0; i < rows.length(); i++) collected.put(rows.getJSONObject(i));
            if (candidatePage + 1 < data.optInt("totalPages", 1)) {
                attendanceCandidatePages(url, candidatePage + 1, collected, reply);
            } else {
                reply.accept(json("items", collected));
            }
        });
    }
    private void attendanceCandidates() {
        attendanceCandidatePages("/api/attendance-workspace/records?mine=true&from=" + from + "&to=" + to, 0, new JSONArray(), data -> {
            JSONArray items = data.optJSONArray("items");
            List<JSONObject> eligible = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (items != null)
                for (int i = 0; i < items.length(); i++) {
                    JSONObject r = items.getJSONObject(i);
                    if (r.optBoolean("canRequest") && !"DA_XAC_NHAN".equals(str(r,"verificationStatus"))) {
                        eligible.add(r);
                        names.add(str(r, "workDate") + " · " + str(r, "shift"));
                    }
                }
            if (names.isEmpty()) {
                toast("Không có công đủ điều kiện trong khoảng ngày đã chọn. Chỉ bổ sung giờ ra sau khi ca kết thúc. Bản ghi phải còn hạn, kỳ chưa khóa và chưa có yêu cầu đang mở.");
                return;
            }
            new MaterialAlertDialogBuilder(this).setTitle("Chọn bản ghi công cá nhân")
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        JSONObject r = eligible.get(which);
                        fetch("/api/attendance-workspace/schedules/" + r.optLong("scheduleId") + "?mine=true",
                                detail -> attendanceProposal(detail.getJSONObject("row")));
                    }).setNegativeButton("Đóng", null).show();
        });
    }

    private void ownOt() {
        fetch("/api/overtime/workspace/context", context -> {
            if (context.optBoolean("canRegister"))
                button(card("Đăng ký tăng ca"), "Tạo đơn OT", true, () -> otForm(null));
        });
        fetch("/api/overtime/workspace/requests?mine=true&" + query(), data -> {
            JSONArray rows = data.optJSONArray("items");
            if (rows == null || rows.length() == 0)
                card("Chưa có đơn OT").addView(text("Đơn đã gửi sẽ hiển thị tại đây.", 14, MUTED, false));
            else
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i), o = row.getJSONObject("ot");
                    LinearLayout box = card(str(o, "requestCode"));
                    badge(box, label(str(o, "status")), str(o, "status"));
                    line(box, "Thời gian đề nghị",
                            time(str(o, "requestedStart")) + " → " + time(str(o, "requestedEnd")));
                    line(box, "Lý do", str(o, "reason"));
                    button(box, "Chi tiết đơn", false,
                            () -> fetch("/api/overtime/workspace/requests/" + o.optLong("overtimeId"), this::otDetail));
                    JSONObject caps = row.optJSONObject("actions");
                    if (caps != null && caps.optBoolean("edit"))
                        button(box, "Sửa đơn chờ duyệt", false,
                                () -> fetch("/api/overtime/workspace/requests/" + o.optLong("overtimeId"),
                                        this::otForm));
                    if (caps != null && caps.optBoolean("cancel"))
                        button(box, "Hủy đơn chờ duyệt", false,
                                () -> reasonAction("Hủy đơn OT",
                                        "/api/overtime/workspace/requests/" + o.optLong("overtimeId") + "/cancel",
                                        str(row, "version")));
                    if (caps != null && caps.optBoolean("cancelRequest"))
                        button(box, "Yêu cầu hủy OT đã duyệt", false, () -> reasonAction("Gửi yêu cầu hủy OT",
                                "/api/overtime/workspace/requests/" + o.optLong("overtimeId") + "/cancel-request",
                                str(row, "version")));
                }
            pages(data);
        });
    }

    private void otDetail(JSONObject d) throws Exception {
        JSONObject row = d.getJSONObject("row"), o = row.getJSONObject("ot");
        StringBuilder info = new StringBuilder("Trạng thái: " + label(str(o, "status")) + "\nĐề nghị: "
                + time(str(o, "requestedStart")) + " → " + time(str(o, "requestedEnd")) + "\nThực tế: "
                + time(str(o, "actualStart")) + " → " + time(str(o, "actualEnd")) + "\nNgười duyệt: "
                + str(row, "approver") + "\nNgười xác nhận: " + str(row, "confirmer") + "\nLý do: " + str(o, "reason"));
        JSONArray audit = d.optJSONArray("audit");
        if (audit != null && audit.length() > 0) {
            info.append("\n\nLịch sử xử lý");
            for (int i = 0; i < audit.length(); i++) {
                JSONObject a = audit.getJSONObject(i);
                info.append("\n").append(utc(str(a, "at"))).append(" · ").append(str(a, "actor")).append(" · ")
                        .append(label(str(a, "action"))).append("\n").append(str(a, "reason"));
            }
        }
        new MaterialAlertDialogBuilder(this).setTitle(str(o, "requestCode")).setMessage(info)
                .setPositiveButton("Đóng", null).show();
    }

    private void ownSupplements() {
        button(card("Thiếu check-in/check-out OT"), "Tạo yêu cầu bổ sung OT", true, () -> supplementCandidates(0));
        fetch("/api/overtime/workspace/supplements?mine=true&" + query(), data -> {
            JSONArray rows = data.optJSONArray("items");
            if (rows == null || rows.length() == 0)
                card("Chưa có yêu cầu bổ sung OT")
                        .addView(text("Hạn gửi: 3 ngày sau ngày kết thúc OT.", 14, MUTED, false));
            else
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    LinearLayout box = card(str(r, "requestCode") + " · #" + r.optLong("requestId"));
                    badge(box, label(str(r, "status")), str(r, "status"));
                    line(box, "Đề nghị", time(str(r, "start")) + " → " + time(str(r, "end")));
                    line(box, "Lý do", str(r, "reason"));
                    line(box, "Người xử lý", str(r, "processor"));
                    line(box, "Kết quả", str(r, "decisionReason"));
                }
            pages(data);
        });
    }

    private void supplementCandidates(long target) {
        fetch("/api/overtime/workspace/supplements/candidates?" + query(), data -> {
            JSONArray items = data.optJSONArray("items");
            List<JSONObject> rows = new ArrayList<>();
            List<String> names = new ArrayList<>();
            if (items != null)
                for (int i = 0; i < items.length(); i++) {
                    JSONObject r = items.getJSONObject(i), o = r.getJSONObject("row").getJSONObject("ot");
                    if (target > 0 && target == o.optLong("overtimeId")) {
                        if (r.optBoolean("eligible"))
                            supplementForm(r);
                        else
                            toast(str(r, "unavailableReason"));
                        return;
                    }
                    if (r.optBoolean("eligible")) {
                        rows.add(r);
                        names.add(str(o, "requestCode") + " · " + time(str(o, "requestedStart")));
                    }
                }
            if (rows.isEmpty()) {
                toast("Chưa có OT đủ điều kiện bổ sung trên trang này. Kiểm tra khoảng ngày và hạn gửi 3 ngày.");
                return;
            }
            new MaterialAlertDialogBuilder(this).setTitle("Chọn lịch OT thiếu giờ")
                    .setItems(names.toArray(new String[0]), (d, i) -> {
                        try {
                            supplementForm(rows.get(i));
                        } catch (Exception e) {
                            toast("Không mở được yêu cầu.");
                        }
                    }).setNegativeButton("Đóng", null).show();
        });
    }

    private LinearLayout form() {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        return box;
    }

    private TextInputEditText input(LinearLayout box, String label, String value, int type) {
        TextInputLayout wrap = new TextInputLayout(this);
        wrap.setHint(label);
        TextInputEditText edit = new TextInputEditText(wrap.getContext());
        edit.setInputType(type);
        edit.setText(value);
        edit.setTextColor(INK);
        wrap.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        box.addView(wrap);
        return edit;
    }

    private class DateField {
        TextInputEditText edit;
        String value;

        DateField(LinearLayout box, String title, String initial, boolean locked) {
            value = initial;
            edit = input(box, title, time(initial), InputType.TYPE_NULL);
            edit.setFocusable(false);
            edit.setCursorVisible(false);
            edit.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_recent_history, 0);
            edit.setOnClickListener(v -> {
                if (locked)
                    return;
                LocalDateTime current;
                try {
                    current = LocalDateTime.parse(value);
                } catch (Exception e) {
                    current = LocalDateTime.of(today(), LocalTime.of(8, 0));
                }
                LocalDateTime c = current;
                pickDate(current.toLocalDate(), date -> new TimePickerDialog(EmployeeActivity.this, (p, h, m) -> {
                    value = LocalDateTime.of(date, LocalTime.of(h, m)).toString();
                    edit.setText(time(value));
                }, c.getHour(), c.getMinute(), true).show());
            });
        }
    }

    private androidx.appcompat.app.AlertDialog dialog(String title, LinearLayout box, String action,
            java.util.function.Consumer<androidx.appcompat.app.AlertDialog> submit) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll)
                .setNegativeButton("Đóng", null).setPositiveButton(action, null).create();
        d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w -> {
            if (!writing)
                submit.accept(d);
        }));
        d.show();
        return d;
    }

    private String value(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private JSONObject json(Object... pairs) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2)
                o.put(String.valueOf(pairs[i]), pairs[i + 1]);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return o;
    }

    private String attendanceProposalLabel(JSONObject row) {
        boolean missingIn = str(row, "actualStart").isEmpty();
        boolean missingOut = str(row, "actualEnd").isEmpty();
        if (missingIn && missingOut) return "Bổ sung check-in và check-out";
        if (missingIn) return "Bổ sung check-in";
        if (missingOut) return "Bổ sung check-out";
        return "Yêu cầu điều chỉnh công";
    }

    private void addAttendanceProposalAction(LinearLayout box, JSONObject row) {
        if (row.optBoolean("checkoutSupplementTooEarly")) {
            button(box, attendanceProposalLabel(row), false, () -> {}).setEnabled(false);
            box.addView(text("Ca còn trong giờ làm việc. Hãy check-out trực tiếp khi kết thúc làm việc.", 12, MUTED, false));
        } else if (row.optBoolean("canRequest") && !"DA_XAC_NHAN".equals(str(row, "verificationStatus"))) {
            button(box, attendanceProposalLabel(row), false,
                    () -> fetch("/api/attendance-workspace/schedules/" + row.optLong("scheduleId") + "?mine=true",
                            d -> attendanceProposal(d.getJSONObject("row"))));
        }
    }

    private void attendanceProposal(JSONObject r) {
        if("DA_XAC_NHAN".equals(str(r,"verificationStatus"))){toast("Công đã được xác nhận");return;}
        if (r.optBoolean("checkoutSupplementTooEarly")) {
            toast("Chỉ bổ sung check-out sau khi ca kết thúc. Hãy check-out trực tiếp.");
            return;
        }
        if (!r.optBoolean("canRequest")) {
            toast("Bản ghi không còn đủ điều kiện gửi yêu cầu.");
            return;
        }
        LinearLayout box = form();
        boolean unrecorded = r.optLong("attendanceId") <= 0;
        boolean confirmed = str(r,"verificationStatus").equals("DA_XAC_NHAN");
        boolean missing = !unrecorded && !confirmed && !str(r,"actualStart").isEmpty() && str(r,"actualEnd").isEmpty();
        boolean missingIn = str(r, "actualStart").isEmpty();
        boolean missingOut = str(r, "actualEnd").isEmpty();
        boolean supplement = missingIn || missingOut;
        String requestLabel = attendanceProposalLabel(r);
        if (supplement) {
            box.addView(text("Loại yêu cầu", 13, INK, true));
            Spinner type = new Spinner(this);
            type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                    new String[] { requestLabel }));
            box.addView(type);
        }
        box.addView(text(
                unrecorded ? "Nhập giờ vào và giờ ra thực tế."
                        : missing ? "Nhập giờ ra thực tế. Giờ vào đã ghi nhận được giữ nguyên."
                        : missingIn && !missingOut ? "Nhập giờ vào thực tế. Giờ ra đã ghi nhận được giữ nguyên."
                        : "Công gốc chỉ thay đổi sau khi được duyệt.",
                13, MUTED, false));
        Spinner result = new Spinner(this);
        result.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[] { "Đi làm", "Vắng" }));
        result.setSelection(!supplement && str(r, "attendanceResult").equals("VANG") ? 1 : 0);
        if (!supplement) box.addView(result);
        DateField start = new DateField(box, "Giờ vào đề nghị", str(r, "actualStart"), missing),
                end = new DateField(box, "Giờ ra đề nghị", str(r, "actualEnd"), missingIn && !missingOut);
        TextInputEditText rests = input(box, "Phút nghỉ thực tế", String.valueOf(r.optInt("actualBreakMinutes")),
                InputType.TYPE_CLASS_NUMBER),
                reason = input(box, "Lý do *", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog(requestLabel, box, "Gửi yêu cầu", d -> {
            try {
                if ((result.getSelectedItemPosition() == 0 && (start.value.isEmpty() || end.value.isEmpty()
                        || !LocalDateTime.parse(end.value).isAfter(LocalDateTime.parse(start.value))))
                        || value(reason).isEmpty()) {
                    reason.setError("Nhập đủ giờ đề nghị và lý do.");
                    return;
                }
                int minutes = result.getSelectedItemPosition() == 1 ? 0 : Integer.parseInt(value(rests));
                if (minutes < 0) {
                    rests.setError("Phút nghỉ không được âm.");
                    return;
                }
                write("/api/attendance-workspace/requests", "POST",
                        json("type", unrecorded ? "MISSING_ATTENDANCE" : missing ? "MISSING_CHECKOUT" : confirmed ? "CONFIRMED_CORRECTION" : "UNCONFIRMED_CORRECTION", "scheduleId", r.optLong("scheduleId"), "attendanceId",
                                r.optLong("attendanceId"), "actualStart",
                                result.getSelectedItemPosition() == 1 ? JSONObject.NULL : start.value, "actualEnd",
                                result.getSelectedItemPosition() == 1 ? JSONObject.NULL : end.value,
                                "actualBreakMinutes", minutes, "attendanceResult",
                                result.getSelectedItemPosition() == 1 ? "VANG" : "DI_LAM", "reason", value(reason)),
                        "Gửi đề xuất công cá nhân để xét duyệt?", d);
            } catch (Exception e) {
                reason.setError("Kiểm tra giờ và phút nghỉ hợp lệ.");
            }
        });
    }

    private class RestFields {
        LinearLayout box;
        List<DateField[]> fields = new ArrayList<>();

        RestFields(LinearLayout parent) {
            box = column();
            parent.addView(box);
            button(parent, "+ Thêm khoảng nghỉ", false, () -> add("", ""));
        }

        void add(String start, String end) {
            LinearLayout rest = column();
            DateField a = new DateField(rest, "Bắt đầu nghỉ", start, false),
                    b = new DateField(rest, "Kết thúc nghỉ", end, false);
            DateField[] pair = { a, b };
            fields.add(pair);
            button(rest, "Xóa khoảng nghỉ", false, () -> {
                fields.remove(pair);
                box.removeView(rest);
            });
            box.addView(rest);
        }

        JSONArray values() throws Exception {
            JSONArray out = new JSONArray();
            for (DateField[] f : fields) {
                if (f[0].value.isEmpty() || f[1].value.isEmpty())
                    throw new IllegalArgumentException();
                out.put(json("breakStart", f[0].value, "breakEnd", f[1].value));
            }
            return out;
        }
    }

    private void otForm(JSONObject detail) {
        JSONObject row = detail == null ? null : detail.optJSONObject("row"),
                o = row == null ? new JSONObject() : row.optJSONObject("ot");
        LinearLayout box = form();
        box.addView(text(
                "Đăng ký trước hoặc hồi tố trong 3 ngày sau ngày kết thúc OT. Khai báo các khoảng nghỉ dự kiến nếu có.",
                13, MUTED, false));
        DateField start = new DateField(box, "Bắt đầu OT *", str(o, "requestedStart"), false),
                end = new DateField(box, "Kết thúc OT *", str(o, "requestedEnd"), false);
        RestFields rests = new RestFields(box);
        JSONArray existing = detail == null ? null : detail.optJSONArray("plannedBreaks");
        if (existing != null)
            for (int i = 0; i < existing.length(); i++) {
                JSONObject b = existing.optJSONObject(i);
                rests.add(str(b, "breakStart"), str(b, "breakEnd"));
            }
        TextInputEditText work = input(box, "Nội dung công việc", row == null ? "" : str(row, "workContent"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE),
                reason = input(box, "Lý do *", str(o, "reason"),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog(detail == null ? "Đăng ký OT" : "Sửa đơn OT", box, "Gửi đơn", d -> {
            try {
                if (start.value.isEmpty() || end.value.isEmpty() || value(reason).isEmpty()) {
                    reason.setError("Nhập đủ ngày giờ và lý do.");
                    return;
                }
                JSONObject payload = json("requestedStart", start.value, "requestedEnd", end.value, "reason",
                        value(reason), "workContent", value(work), "breaks", rests.values());
                write(detail == null ? "/api/overtime/workspace/requests"
                        : "/api/overtime/workspace/requests/" + o.optLong("overtimeId"),
                        detail == null ? "POST" : "PUT",
                        detail == null ? payload : json("expectedVersion", str(row, "version"), "request", payload),
                        "Gửi đơn OT để xét duyệt?", d);
            } catch (Exception e) {
                reason.setError("Kiểm tra đầy đủ các khoảng nghỉ.");
            }
        });
    }

    private void otCheckout(JSONObject row) {
        JSONObject o = row.optJSONObject("ot");
        LinearLayout box = form();
        box.addView(text("Giờ ra lấy từ máy chủ. Nhập khoảng nghỉ thực tế, không tự sao chép nghỉ dự kiến.", 13, MUTED,
                false));
        RestFields rests = new RestFields(box);
        CheckBox none = new CheckBox(this);
        none.setText("Tôi xác nhận không có khoảng nghỉ thực tế");
        box.addView(none);
        dialog("Check-out OT", box, "Check-out", d -> {
            try {
                JSONArray values = rests.values();
                if (values.length() == 0 && !none.isChecked()) {
                    none.setError("Xác nhận không nghỉ hoặc nhập khoảng nghỉ.");
                    return;
                }
                write("/api/overtime/workspace/calendar/" + o.optLong("overtimeId") + "/check-out", "POST",
                        json("expectedVersion", str(row, "version"), "breaks", values, "noBreaksConfirmed",
                                none.isChecked()),
                        "Check-out OT theo giờ máy chủ?", d);
            } catch (Exception e) {
                none.setError("Nhập đầy đủ thời gian nghỉ.");
            }
        });
    }

    private void supplementForm(JSONObject candidate) throws Exception {
        JSONObject row = candidate.getJSONObject("row"), o = row.getJSONObject("ot");
        LinearLayout box = form();
        box.addView(
                text("Bổ sung giờ còn thiếu; hạn gửi 3 ngày. Công gốc chưa thay đổi khi gửi đơn.", 13, MUTED, false));
        DateField start = new DateField(box, "Giờ vào thực tế *", str(o, "actualStart"),
                !str(o, "actualStart").isEmpty()),
                end = new DateField(box, "Giờ ra thực tế *", str(o, "actualEnd"), !str(o, "actualEnd").isEmpty());
        RestFields rests = new RestFields(box);
        CheckBox none = new CheckBox(this);
        none.setText("Tôi xác nhận không có khoảng nghỉ thực tế");
        box.addView(none);
        TextInputEditText reason = input(box, "Lý do *", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog("Bổ sung công OT", box, "Gửi yêu cầu", d -> {
            try {
                JSONArray values = rests.values();
                if (start.value.isEmpty() || end.value.isEmpty() || value(reason).isEmpty()
                        || values.length() == 0 && !none.isChecked()) {
                    reason.setError("Nhập đủ giờ, lý do và xác nhận khoảng nghỉ.");
                    return;
                }
                write("/api/overtime/workspace/supplements/overtime/" + o.optLong("overtimeId"), "POST",
                        json("expectedVersion", str(row, "version"), "start", start.value, "end", end.value, "breaks",
                                values, "noBreaksConfirmed", none.isChecked(), "reason", value(reason)),
                        "Gửi yêu cầu bổ sung giờ OT?", d);
            } catch (Exception e) {
                reason.setError("Kiểm tra các trường đề nghị.");
            }
        });
    }

    private void reasonAction(String title, String path, String version) {
        LinearLayout box = form();
        TextInputEditText reason = input(box, "Lý do *", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dialog(title, box, "Gửi", d -> {
            if (value(reason).isEmpty()) {
                reason.setError("Nhập lý do.");
                return;
            }
            write(path, "POST", json("expectedVersion", version, "reason", value(reason)), title + "?", d);
        });
    }

    private String profileSubPage = "hub";
    private static final int PROFILE_CONTRACT_PAGE_SIZE = 20;
    private int profileContractPage;
    private long profileContractId;
    private boolean profileEditFromAccountInfo = false;
    private boolean profileSoundEnabled = true;
    private boolean profileSecurityLockEnabled = false;
    private String profileGuideQuery = "";
    private String upgradeTier = "plus";

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "NV";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private String formatDateVi(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            return LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return raw;
        }
    }

    private void addProfileSubHeader(String pageTitle, Runnable onBack) {
        findViewById(R.id.employeeHeader).setVisibility(View.GONE);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(4), 0, dp(16));

        TextView backIcon = text("‹", 28, 0xff111827, false);
        backIcon.setGravity(Gravity.CENTER_VERTICAL);
        backIcon.setPadding(dp(4), 0, dp(14), dp(2));
        ((LinearLayout.LayoutParams) backIcon.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) backIcon.getLayoutParams()).width = -2;

        TextView titleTv = text(pageTitle, 20, 0xff111827, true);
        ((LinearLayout.LayoutParams) titleTv.getLayoutParams()).bottomMargin = 0;

        bar.addView(backIcon);
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1));
        bar.setOnClickListener(v -> {
            if (!writing) onBack.run();
        });
        body.addView(bar, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addProfileSectionLabel(String labelText) {
        TextView label = text(labelText, 15, 0xff7b8798, true);
        label.setPadding(dp(2), dp(8), dp(2), dp(6));
        ((LinearLayout.LayoutParams) label.getLayoutParams()).bottomMargin = 0;
        body.addView(label);
    }

    private LinearLayout profileWhiteCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(12);
        card.setLayoutParams(p);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeColor(0xffedf1f7);
        card.setStrokeWidth(dp(1));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout box = column();
        card.addView(box);
        body.addView(card);
        return box;
    }

    private void addProfileMenuRow(LinearLayout cardBox, String iconText, String rowTitle, String rowSubtitle,
                                   int titleColor, String rightValue, boolean showDivider, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            if (!writing && onClick != null) onClick.run();
        });

        if (iconText != null && !iconText.isEmpty()) {
            TextView icon = text(iconText, 17, titleColor == 0xffdc2626 ? 0xffdc2626 : 0xff64748b, false);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(28));
            ip.setMarginEnd(dp(12));
            ip.bottomMargin = 0;
            icon.setLayoutParams(ip);
            row.addView(icon);
        }

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(rowTitle, 15, titleColor, false);
        ((LinearLayout.LayoutParams) t.getLayoutParams()).bottomMargin = (rowSubtitle != null && !rowSubtitle.isEmpty()) ? dp(2) : 0;
        center.addView(t);
        if (rowSubtitle != null && !rowSubtitle.isEmpty()) {
            TextView sub = text(rowSubtitle, 12, 0xff8c98a9, false);
            ((LinearLayout.LayoutParams) sub.getLayoutParams()).bottomMargin = 0;
            center.addView(sub);
        }
        row.addView(center, new LinearLayout.LayoutParams(0, -2, 1));

        if (rightValue != null && !rightValue.isEmpty()) {
            TextView rv = text(rightValue, 14, 0xff1f2937, false);
            LinearLayout.LayoutParams rvp = new LinearLayout.LayoutParams(-2, -2);
            rvp.setMarginEnd(dp(6));
            rvp.bottomMargin = 0;
            rv.setLayoutParams(rvp);
            row.addView(rv);
        }

        TextView chevron = text("›", 20, 0xff94a3b8, false);
        ((LinearLayout.LayoutParams) chevron.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) chevron.getLayoutParams()).width = -2;
        row.addView(chevron);

        cardBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (showDivider) {
            View div = new View(this);
            div.setBackgroundColor(0xfff1f5f9);
            LinearLayout.LayoutParams dpLayout = new LinearLayout.LayoutParams(-1, dp(1));
            dpLayout.setMarginStart(dp(16));
            dpLayout.setMarginEnd(dp(16));
            cardBox.addView(div, dpLayout);
        }
    }

    private void addFeatureBadgeRow(LinearLayout cardBox, String iconText, String rowTitle, String rowSubtitle,
                                    boolean showDivider, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            if (!writing && onClick != null) onClick.run();
        });

        TextView badgeIcon = text(iconText, 17, 0xff2563eb, true);
        badgeIcon.setGravity(Gravity.CENTER);
        badgeIcon.setBackground(rounded(0xffeef2ff, 12));
        LinearLayout.LayoutParams bip = new LinearLayout.LayoutParams(dp(42), dp(42));
        bip.setMarginEnd(dp(14));
        bip.bottomMargin = 0;
        badgeIcon.setLayoutParams(bip);
        row.addView(badgeIcon);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(rowTitle, 15, 0xff111827, true);
        ((LinearLayout.LayoutParams) t.getLayoutParams()).bottomMargin = dp(2);
        center.addView(t);
        if (rowSubtitle != null && !rowSubtitle.isEmpty()) {
            TextView sub = text(rowSubtitle, 13, 0xff8c98a9, false);
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ((LinearLayout.LayoutParams) sub.getLayoutParams()).bottomMargin = 0;
            center.addView(sub);
        }
        row.addView(center, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chevron = text("›", 20, 0xff94a3b8, false);
        ((LinearLayout.LayoutParams) chevron.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) chevron.getLayoutParams()).width = -2;
        row.addView(chevron);

        cardBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (showDivider) {
            View div = new View(this);
            div.setBackgroundColor(0xfff1f5f9);
            LinearLayout.LayoutParams dpLayout = new LinearLayout.LayoutParams(-1, dp(1));
            dpLayout.setMarginStart(dp(14));
            dpLayout.setMarginEnd(dp(14));
            cardBox.addView(div, dpLayout);
        }
    }

    private LinearLayout buildProfileSummaryHeaderCard(JSONObject p, boolean showChevron, Runnable onClick) {
        LinearLayout cardBox = profileWhiteCard();
        cardBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (onClick != null) {
            cardBox.setClickable(true);
            cardBox.setFocusable(true);
            cardBox.setOnClickListener(v -> {
                if (!writing) onClick.run();
            });
        }

        // Avatar tròn giống mẫu tham khảo
        FrameLayout avatarWrap = new FrameLayout(this);
        avatarWrap.setBackground(rounded(0xfff59e0b, 30));
        TextView avatarTv = text(initialsOf(str(p, "fullName")), 19, 0xff111827, true);
        avatarTv.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) avatarTv.getLayoutParams()).bottomMargin = 0;
        avatarWrap.addView(avatarTv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(58), dp(58));
        ap.setMarginEnd(dp(14));
        row.addView(avatarWrap, ap);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        String fullName = str(p, "fullName").isEmpty() ? "Nhân viên" : str(p, "fullName");
        TextView nameTv = text(fullName, 17, 0xff111827, true);
        ((LinearLayout.LayoutParams) nameTv.getLayoutParams()).bottomMargin = dp(2);
        infoCol.addView(nameTv);

        String emailOrUser = !str(p, "email").isEmpty() ? str(p, "email") : str(p, "username");
        TextView emailTv = text(emailOrUser, 14, 0xff7b8798, false);
        emailTv.setSingleLine(true);
        emailTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ((LinearLayout.LayoutParams) emailTv.getLayoutParams()).bottomMargin = dp(2);
        infoCol.addView(emailTv);

        String hireDate = formatDateVi(str(p, "hireDate"));
        android.text.SpannableString dateSpan = new android.text.SpannableString("Ngày vào làm: " + hireDate);
        dateSpan.setSpan(new android.text.style.ForegroundColorSpan(0xff111827), 14, dateSpan.length(), 0);
        TextView dateTv = text("", 13, 0xff8c98a9, false);
        dateTv.setText(dateSpan);
        ((LinearLayout.LayoutParams) dateTv.getLayoutParams()).bottomMargin = 0;
        infoCol.addView(dateTv);

        row.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1));

        if (showChevron) {
            TextView chev = text("›", 22, 0xff94a3b8, false);
            ((LinearLayout.LayoutParams) chev.getLayoutParams()).bottomMargin = 0;
            ((LinearLayout.LayoutParams) chev.getLayoutParams()).width = -2;
            row.addView(chev);
        }

        cardBox.addView(row);
        return cardBox;
    }

    private void profile() {
        switch (profileSubPage) {
            case "account_info":
                profileAccountInfoPage();
                break;
            case "edit_profile":
                profileEditPage();
                break;
            case "upgrade_plan":
                profileUpgradePlanPage();
                break;
            case "manage_plan":
                profileManagePlanPage();
                break;
            case "salary_items":
                profileSalaryItemsPage();
                break;
            case "contracts":
                profileContractsAndDecisionsPage();
                break;
            case "contract_detail":
                profileContractDetailPage();
                break;
            case "settings":
                profileSettingsPage();
                break;
            case "guide":
                profileGuidePage();
                break;
            case "feedback":
                profileFeedbackPage();
                break;
            default:
                profileHubPage();
                break;
        }
    }

    /**
     * Màn hình chính "Cá nhân" (Hồ sơ) theo đúng mẫu tham khảo Hình 7
     */
    private void profileHubPage() {
        findViewById(R.id.employeeHeader).setVisibility(View.GONE);
        JSONObject p = session.profile();

        // Thanh tiêu đề trên cùng: "Cá nhân" bên trái + 3 nút tròn bên phải (giống Hình 7)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(14));

        TextView pageTitle = text("Cá nhân", 24, 0xff111827, true);
        ((LinearLayout.LayoutParams) pageTitle.getLayoutParams()).bottomMargin = 0;
        topBar.addView(pageTitle, new LinearLayout.LayoutParams(0, -2, 1));

        topBar.addView(circleActionButton("📋", "Sao chép mã NV", () -> copySupportInfo(p)));
        topBar.addView(circleActionButton("🔄", "Tải lại hồ sơ", this::refreshProfileData));
        topBar.addView(circleActionButton("✎", "Chỉnh sửa thông tin", () -> {
            profileEditFromAccountInfo = false;
            profileSubPage = "edit_profile";
            render();
        }));
        body.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        // 1. Thẻ thông tin cá nhân trên cùng (bấm vào mở trang < Thông tin tài khoản giống Hình 6 & 7)
        buildProfileSummaryHeaderCard(p, true, () -> {
            profileSubPage = "account_info";
            render();
        });

        // 2. Banner vàng kim nổi bật "NÂNG CẤP ỨNG DỤNG / Gói trọn đời" (giống hệt Hình 7 -> bấm vào mở trang Nâng cấp gói Plus / Premium)
        LinearLayout goldBanner = new LinearLayout(this);
        goldBanner.setOrientation(LinearLayout.HORIZONTAL);
        goldBanner.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable goldBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xffeab308, 0xfffef08a, 0xffeab308});
        goldBg.setCornerRadius(dp(16));
        goldBanner.setBackground(goldBg);
        goldBanner.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams gbp = new LinearLayout.LayoutParams(-1, -2);
        gbp.bottomMargin = dp(10);
        goldBanner.setLayoutParams(gbp);
        goldBanner.setClickable(true);
        goldBanner.setFocusable(true);
        goldBanner.setOnClickListener(v -> {
            upgradeTier = "plus";
            profileSubPage = "upgrade_plan";
            render();
        });

        LinearLayout goldTextCol = new LinearLayout(this);
        goldTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView gt1 = text("NÂNG CẤP ỨNG DỤNG", 16, Color.WHITE, true);
        ((LinearLayout.LayoutParams) gt1.getLayoutParams()).bottomMargin = dp(2);
        TextView gt2 = text("Gói trọn đời", 14, Color.WHITE, false);
        ((LinearLayout.LayoutParams) gt2.getLayoutParams()).bottomMargin = 0;
        goldTextCol.addView(gt1);
        goldTextCol.addView(gt2);
        goldBanner.addView(goldTextCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView diamond = text("💎   ›", 20, Color.WHITE, true);
        ((LinearLayout.LayoutParams) diamond.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) diamond.getLayoutParams()).width = -2;
        goldBanner.addView(diamond);
        body.addView(goldBanner);

        // 3. Pill bar viền vàng nhạt "👑 Quản lý gói nâng cấp" (giống hệt Hình 7 -> bấm vào mở trang < Quản lý gói nâng cấp)
        LinearLayout pillBar = new LinearLayout(this);
        pillBar.setOrientation(LinearLayout.HORIZONTAL);
        pillBar.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable pillBg = rounded(0xfffff9db, 24);
        pillBg.setStroke(dp(1), 0xffe5c158);
        pillBar.setBackground(pillBg);
        pillBar.setPadding(dp(16), dp(11), dp(16), dp(11));
        LinearLayout.LayoutParams pbp = new LinearLayout.LayoutParams(-1, -2);
        pbp.bottomMargin = dp(14);
        pillBar.setLayoutParams(pbp);
        pillBar.setClickable(true);
        pillBar.setFocusable(true);
        pillBar.setOnClickListener(v -> {
            profileSubPage = "manage_plan";
            render();
        });
        TextView crownTitle = text("👑   Quản lý gói nâng cấp", 15, 0xff111827, true);
        ((LinearLayout.LayoutParams) crownTitle.getLayoutParams()).bottomMargin = 0;
        pillBar.addView(crownTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView pillChev = text("›", 20, 0xff111827, true);
        ((LinearLayout.LayoutParams) pillChev.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) pillChev.getLayoutParams()).width = -2;
        pillBar.addView(pillChev);
        body.addView(pillBar);

        // 4. Mục "Quản lý" — 4 ô icon ngang trong thẻ trắng (Thông báo: Bật/Tắt thông báo nhảy trên màn hình chính với icon khác nhau!)
        addProfileSectionLabel("Quản lý");
        LinearLayout manageCard = profileWhiteCard();
        manageCard.setPadding(dp(8), dp(14), dp(8), dp(14));
        LinearLayout gridRow = new LinearLayout(this);
        gridRow.setOrientation(LinearLayout.HORIZONTAL);
        gridRow.addView(buildNotificationToggleGridItem(), new LinearLayout.LayoutParams(0, -2, 1));
        gridRow.addView(quickGridItem("📄", "Phiếu lương", () -> {
            section = "salary";
            render();
        }), new LinearLayout.LayoutParams(0, -2, 1));
        gridRow.addView(quickGridItem("💰", "Phụ cấp", () -> {
            profileSubPage = "salary_items";
            render();
        }), new LinearLayout.LayoutParams(0, -2, 1));
        gridRow.addView(quickGridItem("📑", "Hợp đồng", () -> {
            profileSubPage = "contracts";
            render();
        }), new LinearLayout.LayoutParams(0, -2, 1));
        manageCard.addView(gridRow);

        // 5. Mục "Lương và phúc lợi" — Thẻ trắng danh sách có icon bên trái + › bên phải
        addProfileSectionLabel("Lương và phúc lợi");
        LinearLayout salaryGroup = profileWhiteCard();
        addProfileMenuRow(salaryGroup, "📄", "Phiếu lương cá nhân đã công bố",
                "Xem thu nhập, phạt đi trễ từng phút & bảng công 22 ngày", 0xff111827, null, true, () -> {
                    section = "salary";
                    render();
                });
        addProfileMenuRow(salaryGroup, "🏦", "Khoản lương / phụ cấp đã công bố",
                "Định mức phụ cấp hợp đồng & chi tiết các khoản lương theo kỳ", 0xff111827, null, true, () -> {
                    profileSubPage = "salary_items";
                    render();
                });
        addProfileMenuRow(salaryGroup, "📑", "Hợp đồng và quyết định",
                "Hợp đồng lao động, quyết định khen thưởng & kỷ luật", 0xff111827, null, false, () -> {
                    profileContractPage = 0;
                    showProfileContracts();
                });

        // 6. Mục "Ứng dụng" — Giống hệt Hình 7 (Cài đặt chung, Góp ý cho ứng dụng, Hướng dẫn sử dụng)
        addProfileSectionLabel("Ứng dụng");
        LinearLayout appGroup = profileWhiteCard();
        addProfileMenuRow(appGroup, "⚙️", "Cài đặt chung", null, 0xff111827, null, true, () -> {
            profileSubPage = "settings";
            render();
        });
        addProfileMenuRow(appGroup, "✉️", "Góp ý cho ứng dụng", null, 0xff111827, null, true, () -> {
            profileSubPage = "feedback";
            render();
        });
        addProfileMenuRow(appGroup, "✨", "Hướng dẫn sử dụng", null, 0xff111827, null, false, () -> {
            profileSubPage = "guide";
            render();
        });

        // 7. Mục "Tài khoản" — Giống Hình 6 (Thông tin tài khoản, Chỉnh sửa hồ sơ, Đổi mật khẩu, Đăng xuất)
        addProfileSectionLabel("Tài khoản");
        LinearLayout accountGroup = profileWhiteCard();
        addProfileMenuRow(accountGroup, "👤", "Thông tin tài khoản & Nhân sự",
                "Xem đầy đủ mã NV, ngày sinh, địa chỉ, phòng ban", 0xff111827, null, true, () -> {
                    profileSubPage = "account_info";
                    render();
                });
        addProfileMenuRow(accountGroup, "✏️", "Chỉnh sửa hồ sơ",
                "Cập nhật số điện thoại, email, địa chỉ liên hệ", 0xff111827, null, true, () -> {
                    profileEditFromAccountInfo = false;
                    profileSubPage = "edit_profile";
                    render();
                });
        addProfileMenuRow(accountGroup, "🔑", "Đổi mật khẩu", null, 0xff111827, null, true, this::changePassword);
        addProfileMenuRow(accountGroup, "🚪", "Đăng xuất", null, 0xffdc2626, null, false, this::confirmSignOut);
    }

    private View circleActionButton(String icon, String contentDesc, Runnable onClick) {
        TextView btn = text(icon, 16, 0xff1e293b, true);
        btn.setGravity(Gravity.CENTER);
        btn.setContentDescription(contentDesc);
        android.graphics.drawable.GradientDrawable bg = rounded(Color.WHITE, 20);
        bg.setStroke(dp(1), 0xffe2e8f0);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMarginStart(dp(8));
        lp.bottomMargin = 0;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            if (!writing) onClick.run();
        });
        return btn;
    }

    private LinearLayout quickGridItem(String iconText, String labelText, Runnable onClick) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(4), dp(4), dp(4), dp(4));
        col.setClickable(true);
        col.setFocusable(true);
        col.setOnClickListener(v -> {
            if (!writing && onClick != null) onClick.run();
        });

        TextView iconBox = text(iconText, 18, 0xff334155, false);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(rounded(0xfff1f5f9, 14));
        LinearLayout.LayoutParams ibp = new LinearLayout.LayoutParams(dp(48), dp(48));
        ibp.bottomMargin = dp(8);
        iconBox.setLayoutParams(ibp);
        col.addView(iconBox);

        TextView lbl = text(labelText, 13, 0xff1f2937, false);
        lbl.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) lbl.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) lbl.getLayoutParams()).width = -2;
        col.addView(lbl);
        return col;
    }

    private LinearLayout buildNotificationToggleGridItem() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(4), dp(4), dp(4), dp(4));
        col.setClickable(true);
        col.setFocusable(true);

        boolean enabled = MobileNotifications.isPopupEnabled(this);

        TextView iconBox = text(enabled ? "🔔" : "🔕", 19, enabled ? 0xff1d4ed8 : 0xff94a3b8, false);
        iconBox.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable boxBg = rounded(enabled ? 0xffdbeafe : 0xfff1f5f9, 14);
        boxBg.setStroke(dp(1), enabled ? 0xff3b82f6 : 0xffcbd5e1);
        iconBox.setBackground(boxBg);
        LinearLayout.LayoutParams ibp = new LinearLayout.LayoutParams(dp(48), dp(48));
        ibp.bottomMargin = dp(8);
        iconBox.setLayoutParams(ibp);
        col.addView(iconBox);

        TextView lbl = text(enabled ? "Thông báo" : "Tắt TB nhảy", 13, enabled ? 0xff1d4ed8 : 0xff64748b, enabled);
        lbl.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) lbl.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) lbl.getLayoutParams()).width = -2;
        col.addView(lbl);

        col.setOnClickListener(v -> {
            boolean nextState = !MobileNotifications.isPopupEnabled(this);
            MobileNotifications.setPopupEnabled(this, nextState);
            if (nextState && android.os.Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
                }
            }
            iconBox.setText(nextState ? "🔔" : "🔕");
            iconBox.setTextColor(nextState ? 0xff1d4ed8 : 0xff94a3b8);
            android.graphics.drawable.GradientDrawable newBg = rounded(nextState ? 0xffdbeafe : 0xfff1f5f9, 14);
            newBg.setStroke(dp(1), nextState ? 0xff3b82f6 : 0xffcbd5e1);
            iconBox.setBackground(newBg);
            lbl.setText(nextState ? "Thông báo" : "Tắt TB nhảy");
            lbl.setTextColor(nextState ? 0xff1d4ed8 : 0xff64748b);
            lbl.setTypeface(null, nextState ? Typeface.BOLD : Typeface.NORMAL);
            toast(nextState
                    ? "🔔 Đã BẬT thông báo nhảy trên màn hình chính điện thoại."
                    : "🔕 Đã TẮT thông báo nhảy trên màn hình chính (vẫn lưu trong hộp thư thông báo).");
        });

        return col;
    }

    /**
     * Trang "< Nâng cấp gói" (HRM Plus / HRM Premium)
     */
    private void profileUpgradePlanPage() {
        findViewById(R.id.employeeHeader).setVisibility(View.GONE);
        body.setPadding(0, 0, 0, 0);

        boolean isPremium = "premium".equalsIgnoreCase(upgradeTier);

        // 1. KHỐI BANNER XANH DƯƠNG TRÊN CÙNG (Giống hệt mẫu tham khảo)
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable heroBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff1e62d0, 0xff2563eb});
        hero.setBackground(heroBg);
        hero.setPadding(dp(16), dp(14), dp(16), dp(22));

        // Thanh điều hướng trong banner xanh: nút tròn "<" bên trái | "Nâng cấp gói" ở giữa | nút tròn "🎧" bên phải
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nrp = new LinearLayout.LayoutParams(-1, -2);
        nrp.bottomMargin = dp(18);
        navRow.setLayoutParams(nrp);

        TextView backBtn = text("‹", 24, Color.WHITE, false);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setBackground(rounded(0xff4f86e8, 20));
        LinearLayout.LayoutParams bbp = new LinearLayout.LayoutParams(dp(38), dp(38));
        bbp.bottomMargin = 0;
        backBtn.setLayoutParams(bbp);
        backBtn.setOnClickListener(v -> {
            profileSubPage = "hub";
            render();
        });
        navRow.addView(backBtn);

        TextView navTitle = text("Nâng cấp gói", 18, Color.WHITE, true);
        navTitle.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) navTitle.getLayoutParams()).bottomMargin = 0;
        navRow.addView(navTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView supportBtn = text("🎧", 16, Color.WHITE, false);
        supportBtn.setGravity(Gravity.CENTER);
        supportBtn.setBackground(rounded(0xff4f86e8, 20));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(dp(38), dp(38));
        sbp.bottomMargin = 0;
        supportBtn.setLayoutParams(sbp);
        supportBtn.setOnClickListener(v -> toast("Bộ phận CSKH & Tư vấn gói nâng cấp 24/7."));
        navRow.addView(supportBtn);
        hero.addView(navRow);

        // Nội dung giới thiệu gói + hình minh họa màn hình máy tính / kim cương bên phải
        LinearLayout heroBody = new LinearLayout(this);
        heroBody.setOrientation(LinearLayout.HORIZONTAL);
        heroBody.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout heroTextCol = new LinearLayout(this);
        heroTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView planHeading = text(isPremium ? "HRM Premium" : "HRM Plus", 20, Color.WHITE, true);
        ((LinearLayout.LayoutParams) planHeading.getLayoutParams()).bottomMargin = dp(6);
        heroTextCol.addView(planHeading);

        String planDescText = isPremium
                ? "Giải pháp toàn diện cho doanh nghiệp cần công cụ quản lý nâng cao, tự động hóa và báo cáo chuyên sâu."
                : "Lý tưởng cho cá nhân và đội nhóm nhỏ muốn loại bỏ các giới hạn cơ bản và làm việc hiệu quả hơn.";
        TextView planDesc = text(planDescText, 14, 0xffdbeafe, false);
        ((LinearLayout.LayoutParams) planDesc.getLayoutParams()).bottomMargin = 0;
        heroTextCol.addView(planDesc);
        heroBody.addView(heroTextCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Hình minh họa màn hình máy tính (kèm kim cương vàng khi chọn Premium)
        FrameLayout monitorGraphic = new FrameLayout(this);
        monitorGraphic.setBackground(rounded(0xff3b82f6, 16));
        monitorGraphic.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView monitorIcon = text(isPremium ? "💎\n🖥️" : "🖥️", isPremium ? 24 : 36, Color.WHITE, true);
        monitorIcon.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) monitorIcon.getLayoutParams()).bottomMargin = 0;
        monitorGraphic.addView(monitorIcon, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams mgp = new LinearLayout.LayoutParams(dp(88), dp(82));
        mgp.setMarginStart(dp(12));
        heroBody.addView(monitorGraphic, mgp);

        hero.addView(heroBody);
        body.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        // 2. KHỐI NỘI DUNG TRẮNG BÊN DƯỚI
        LinearLayout mainBox = column();
        mainBox.setBackgroundColor(Color.WHITE);
        mainBox.setPadding(dp(16), dp(18), dp(16), dp(20));

        // Thanh chuyển đổi Tab "Plus" | "Premium" dạng viên thuốc (Pill Segmented Control)
        LinearLayout tabPill = new LinearLayout(this);
        tabPill.setOrientation(LinearLayout.HORIZONTAL);
        tabPill.setBackground(rounded(0xffeaeef4, 24));
        tabPill.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams tpp = new LinearLayout.LayoutParams(-1, -2);
        tpp.setMarginStart(dp(24));
        tpp.setMarginEnd(dp(24));
        tpp.bottomMargin = dp(18);
        tabPill.setLayoutParams(tpp);

        TextView plusTab = text("Plus", 15, !isPremium ? 0xff111827 : 0xff7b8798, true);
        plusTab.setGravity(Gravity.CENTER);
        plusTab.setPadding(0, dp(9), 0, dp(9));
        if (!isPremium) plusTab.setBackground(rounded(Color.WHITE, 20));
        ((LinearLayout.LayoutParams) plusTab.getLayoutParams()).bottomMargin = 0;
        plusTab.setOnClickListener(v -> {
            if (isPremium) {
                upgradeTier = "plus";
                render();
            }
        });

        TextView premiumTab = text("Premium", 15, isPremium ? 0xff111827 : 0xff7b8798, true);
        premiumTab.setGravity(Gravity.CENTER);
        premiumTab.setPadding(0, dp(9), 0, dp(9));
        if (isPremium) premiumTab.setBackground(rounded(Color.WHITE, 20));
        ((LinearLayout.LayoutParams) premiumTab.getLayoutParams()).bottomMargin = 0;
        premiumTab.setOnClickListener(v -> {
            if (!isPremium) {
                upgradeTier = "premium";
                render();
            }
        });

        tabPill.addView(plusTab, new LinearLayout.LayoutParams(0, -2, 1));
        tabPill.addView(premiumTab, new LinearLayout.LayoutParams(0, -2, 1));
        mainBox.addView(tabPill);

        // Thẻ Giá gói nâng cấp (Plus: 300,000 đ / 600,000 đ nền xanh nhạt | Premium: 400,000 đ / 800,000 đ nền vàng kim)
        LinearLayout priceCard = new LinearLayout(this);
        priceCard.setOrientation(LinearLayout.HORIZONTAL);
        priceCard.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable priceBg = rounded(isPremium ? 0xfffde047 : 0xffeef5ff, 14);
        priceBg.setStroke(dp(1.5f), isPremium ? 0xffca8a04 : 0xff2563eb);
        priceCard.setBackground(priceBg);
        priceCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams pcp = new LinearLayout.LayoutParams(-1, -2);
        pcp.bottomMargin = dp(18);
        priceCard.setLayoutParams(pcp);

        LinearLayout priceLeft = new LinearLayout(this);
        priceLeft.setOrientation(LinearLayout.VERTICAL);
        TextView pl1 = text("Gói nâng cấp chỉ", 16, 0xff111827, true);
        ((LinearLayout.LayoutParams) pl1.getLayoutParams()).bottomMargin = dp(4);
        TextView pl2 = text(isPremium ? "Tiết kiệm 400,000 đ" : "Tiết kiệm 300,000 đ", 14, isPremium ? 0xff854d0e : 0xff7b8798, false);
        ((LinearLayout.LayoutParams) pl2.getLayoutParams()).bottomMargin = 0;
        priceLeft.addView(pl1);
        priceLeft.addView(pl2);

        LinearLayout priceRight = new LinearLayout(this);
        priceRight.setOrientation(LinearLayout.VERTICAL);
        priceRight.setGravity(Gravity.END);
        TextView pr1 = text(isPremium ? "400,000 đ" : "300,000 đ", 18, 0xff111827, true);
        pr1.setGravity(Gravity.END);
        ((LinearLayout.LayoutParams) pr1.getLayoutParams()).bottomMargin = dp(4);
        TextView pr2 = text(isPremium ? "800,000 đ" : "600,000 đ", 14, isPremium ? 0xff854d0e : 0xff7b8798, false);
        pr2.setGravity(Gravity.END);
        pr2.setPaintFlags(pr2.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        ((LinearLayout.LayoutParams) pr2.getLayoutParams()).bottomMargin = 0;
        priceRight.addView(pr1);
        priceRight.addView(pr2);

        priceCard.addView(priceLeft, new LinearLayout.LayoutParams(0, -2, 1));
        priceCard.addView(priceRight, new LinearLayout.LayoutParams(-2, -2));
        mainBox.addView(priceCard);

        // Danh sách tính năng kèm dấu tích xanh lá (✔) bên phải giống hệt ảnh tham khảo
        TextView featCountTv = text(isPremium ? "Bao gồm 22 tính năng:" : "Bao gồm 8 tính năng:", 15, 0xff7b8798, false);
        ((LinearLayout.LayoutParams) featCountTv.getLayoutParams()).bottomMargin = dp(12);
        mainBox.addView(featCountTv);

        String[][] features = isPremium
                ? new String[][]{
                        {"📱", "+9 tính năng của gói Plus", ""},
                        {"🔍", "Quản lý công việc theo ca", ""},
                        {"✈️", "Gửi yêu cầu tăng ca", ""},
                        {"💵", "Tùy chỉnh tiền lương", ""},
                        {"👛", "Quản lý phép năm", "Cấu hình quota ngày phép năm, tích lũy phép tự động hàng tháng và báo cáo số ngày nghỉ phép của nhân viên."},
                        {"👤", "Giới hạn thời gian chấm công", ""},
                        {"🌙", "Hỗ trợ chấm công qua đêm", ""},
                        {"📱", "Phạt tiền khi quên chấm ca", ""},
                        {"🔄", "Hỗ trợ kỹ thuật 24/7", ""}
                }
                : new String[][]{
                        {"🚫", "Trải nghiệm không quảng cáo", "Tính năng chỉ áp dụng cho chủ nhóm, không áp dụng cho nhân viên tham gia."},
                        {"📊", "Xuất file Excel không giới hạn", ""},
                        {"📈", "Xem thống kê toàn diện", ""},
                        {"💻", "Thêm danh mục thoải mái", ""},
                        {"🖼️", "Lưu trữ ảnh không giới hạn", ""},
                        {"👤", "Phân quyền chấm công nâng cao", "Thiết lập và quản lý vai trò nhân viên một cách chi tiết."},
                        {"📱", "Sử dụng trên mọi thiết bị", ""},
                        {"☁️", "Sao lưu dữ liệu tự động & an toàn", ""}
                };

        for (String[] f : features) {
            LinearLayout fRow = new LinearLayout(this);
            fRow.setOrientation(LinearLayout.HORIZONTAL);
            fRow.setGravity(Gravity.CENTER_VERTICAL);
            fRow.setPadding(0, dp(10), 0, dp(10));

            TextView fIcon = text(f[0], 22, 0xff2563eb, false);
            fIcon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams fip = new LinearLayout.LayoutParams(dp(40), dp(40));
            fip.setMarginEnd(dp(12));
            fip.bottomMargin = 0;
            fIcon.setLayoutParams(fip);
            fRow.addView(fIcon);

            LinearLayout fCenter = new LinearLayout(this);
            fCenter.setOrientation(LinearLayout.VERTICAL);
            TextView ft = text(f[1], 15, 0xff111827, true);
            ((LinearLayout.LayoutParams) ft.getLayoutParams()).bottomMargin = f[2].isEmpty() ? 0 : dp(3);
            fCenter.addView(ft);
            if (!f[2].isEmpty()) {
                TextView fs = text(f[2], 13, 0xff64748b, false);
                ((LinearLayout.LayoutParams) fs.getLayoutParams()).bottomMargin = 0;
                fCenter.addView(fs);
            }
            fRow.addView(fCenter, new LinearLayout.LayoutParams(0, -2, 1));

            TextView checkBadge = text("✓", 13, Color.WHITE, true);
            checkBadge.setGravity(Gravity.CENTER);
            checkBadge.setBackground(rounded(0xff22c55e, 12));
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(dp(24), dp(24));
            cbp.setMarginStart(dp(10));
            cbp.bottomMargin = 0;
            checkBadge.setLayoutParams(cbp);
            fRow.addView(checkBadge);

            mainBox.addView(fRow);
        }

        // 3. KHỐI CHÂN TRANG ĐĂNG KÝ GÓI (Giống hệt ảnh tham khảo)
        View footerDivider = new View(this);
        footerDivider.setBackgroundColor(0xffe2e8f0);
        LinearLayout.LayoutParams fdp = new LinearLayout.LayoutParams(-1, dp(1));
        fdp.topMargin = dp(12);
        fdp.bottomMargin = dp(14);
        mainBox.addView(footerDivider, fdp);

        TextView onceNote = text("Duy nhất một lần thanh toán, toàn quyền sử dụng\ntrọn bộ tính năng vĩnh viễn.", 12, 0xff8c98a9, false);
        onceNote.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) onceNote.getLayoutParams()).bottomMargin = dp(10);
        mainBox.addView(onceNote);

        LinearLayout linksRow = new LinearLayout(this);
        linksRow.setOrientation(LinearLayout.HORIZONTAL);
        linksRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lrp = new LinearLayout.LayoutParams(-1, -2);
        lrp.bottomMargin = dp(14);
        linksRow.setLayoutParams(lrp);

        String[] linkNames = new String[]{"Khôi phục", "Điều khoản", "Bảo mật"};
        for (int i = 0; i < linkNames.length; i++) {
            final String ln = linkNames[i];
            TextView ltv = text(ln, 13, 0xff1e40af, false);
            ltv.setPaintFlags(ltv.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            ((LinearLayout.LayoutParams) ltv.getLayoutParams()).bottomMargin = 0;
            ((LinearLayout.LayoutParams) ltv.getLayoutParams()).width = -2;
            ltv.setOnClickListener(v -> toast("Đã chọn: " + ln));
            linksRow.addView(ltv);
            if (i + 1 < linkNames.length) {
                TextView dot = text("  ·  ", 13, 0xff94a3b8, false);
                ((LinearLayout.LayoutParams) dot.getLayoutParams()).bottomMargin = 0;
                ((LinearLayout.LayoutParams) dot.getLayoutParams()).width = -2;
                linksRow.addView(dot);
            }
        }
        mainBox.addView(linksRow);

        MaterialButton subscribeBtn = new MaterialButton(this);
        subscribeBtn.setText(isPremium ? "Đăng ký gói Premium" : "Đăng ký gói Plus");
        subscribeBtn.setAllCaps(false);
        subscribeBtn.setTextSize(16);
        subscribeBtn.setTextColor(isPremium ? 0xff111827 : Color.WHITE);
        subscribeBtn.setTypeface(null, Typeface.BOLD);
        subscribeBtn.setCornerRadius(dp(12));
        subscribeBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isPremium ? 0xfffacc15 : 0xff1d61e7));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, dp(52));
        subscribeBtn.setLayoutParams(subParams);
        subscribeBtn.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(isPremium ? "Đăng ký HRM Premium" : "Đăng ký HRM Plus")
                    .setMessage("Gói nâng cấp: " + (isPremium ? "HRM Premium (400,000 đ - Trọn đời)" : "HRM Plus (300,000 đ - Trọn đời)")
                            + "\n\nVui lòng xác nhận thanh toán để mở khóa toàn bộ tính năng nâng cao.")
                    .setNegativeButton("Đóng", null)
                    .setPositiveButton("Thanh toán ngay", (d, w) -> toast("Tính năng cổng thanh toán đang được kết nối."))
                    .show();
        });
        mainBox.addView(subscribeBtn);

        body.addView(mainBox, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * Trang "< Quản lý gói nâng cấp" — Giống hệt Hình 5 (tiêu đề + nút đồng bộ ⇄ bên phải + hộp trống ở giữa màn hình)
     */
    private void profileManagePlanPage() {
        findViewById(R.id.employeeHeader).setVisibility(View.GONE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(4), 0, dp(16));

        TextView backIcon = text("‹", 28, 0xff111827, false);
        backIcon.setGravity(Gravity.CENTER_VERTICAL);
        backIcon.setPadding(dp(4), 0, dp(14), dp(2));
        ((LinearLayout.LayoutParams) backIcon.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) backIcon.getLayoutParams()).width = -2;
        backIcon.setOnClickListener(v -> {
            profileSubPage = "hub";
            render();
        });

        TextView titleTv = text("Quản lý gói nâng cấp", 20, 0xff111827, true);
        ((LinearLayout.LayoutParams) titleTv.getLayoutParams()).bottomMargin = 0;
        titleTv.setOnClickListener(v -> {
            profileSubPage = "hub";
            render();
        });

        bar.addView(backIcon);
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(circleActionButton("⇄", "Đồng bộ gói nâng cấp", () -> toast("Đã kiểm tra và đồng bộ trạng thái gói nâng cấp.")));
        body.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // Hộp rỗng minh họa ở giữa trang giống hệt ảnh tham khảo
        LinearLayout emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setPadding(dp(24), dp(80), dp(24), dp(80));

        TextView sparklesTop = text("   ✦       🐝\n      ╭───╯", 16, 0xff94a3b8, false);
        sparklesTop.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) sparklesTop.getLayoutParams()).bottomMargin = dp(2);
        emptyBox.addView(sparklesTop);

        TextView boxArt = text("📦", 84, 0xffcbd5e1, false);
        boxArt.setGravity(Gravity.CENTER);
        boxArt.setAlpha(0.75f);
        ((LinearLayout.LayoutParams) boxArt.getLayoutParams()).bottomMargin = dp(8);
        emptyBox.addView(boxArt);

        TextView sparklesBottom = text("✦                  ✖", 15, 0xff94a3b8, false);
        sparklesBottom.setGravity(Gravity.CENTER);
        ((LinearLayout.LayoutParams) sparklesBottom.getLayoutParams()).bottomMargin = dp(16);
        emptyBox.addView(sparklesBottom);

        body.addView(emptyBox, new LinearLayout.LayoutParams(-1, -2));
    }
    private void profileAccountInfoPage() {
        JSONObject p = session.profile();
        addProfileSubHeader("Thông tin tài khoản", () -> {
            profileSubPage = "hub";
            render();
        });

        // Thẻ tóm tắt trên cùng giống Hình 6
        buildProfileSummaryHeaderCard(p, false, null);

        // Thẻ đầy đủ 9 trường thông tin hồ sơ nhân sự (không mất bất kỳ thông tin nào)
        addProfileSectionLabel("Hồ sơ nhân sự chi tiết");
        LinearLayout detailCard = profileWhiteCard();
        String[][] fields = new String[][]{
                {"Mã nhân viên", str(p, "employeeCode")},
                {"Họ và tên", str(p, "fullName")},
                {"Tên đăng nhập", str(p, "username")},
                {"Phòng ban", str(p, "departmentName")},
                {"Chức vụ", str(p, "positionName")},
                {"Ngày vào làm", formatDateVi(str(p, "hireDate"))},
                {"Ngày sinh", formatDateVi(str(p, "birthDate"))},
                {"Số điện thoại", str(p, "phone")},
                {"Email", str(p, "email")},
                {"Địa chỉ liên hệ", str(p, "address")}
        };
        for (int i = 0; i < fields.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            TextView k = text(fields[i][0], 14, 0xff64748b, false);
            ((LinearLayout.LayoutParams) k.getLayoutParams()).bottomMargin = 0;
            String valStr = (fields[i][1] == null || fields[i][1].isEmpty()) ? "—" : fields[i][1];
            TextView v = text(valStr, 14, 0xff111827, true);
            v.setGravity(Gravity.END);
            ((LinearLayout.LayoutParams) v.getLayoutParams()).bottomMargin = 0;
            row.addView(k, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
            detailCard.addView(row);
            if (i + 1 < fields.length) {
                View div = new View(this);
                div.setBackgroundColor(0xfff1f5f9);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
                dlp.setMarginStart(dp(16));
                dlp.setMarginEnd(dp(16));
                detailCard.addView(div, dlp);
            }
        }

        // Mục "Tùy chọn" giống Hình 6
        addProfileSectionLabel("Tùy chọn");
        LinearLayout optCard = profileWhiteCard();
        addProfileMenuRow(optCard, "👤", "Chỉnh sửa thông tin", null, 0xff111827, null, true, () -> {
            profileEditFromAccountInfo = true;
            profileSubPage = "edit_profile";
            render();
        });
        addProfileMenuRow(optCard, "🏦", "Khoản lương / phụ cấp đã công bố", null, 0xff111827, null, true, () -> {
            profileSubPage = "salary_items";
            render();
        });
        addProfileMenuRow(optCard, "🛡️", "Sao chép thông tin hỗ trợ", null, 0xff111827, "📋", false, () -> copySupportInfo(p));

        // Mục "Dữ liệu" giống Hình 6
        addProfileSectionLabel("Dữ liệu");
        LinearLayout dataCard = profileWhiteCard();
        addProfileMenuRow(dataCard, "⚠️", "Làm mới dữ liệu ứng dụng", null, 0xffd97706, "🔄", false, this::refreshProfileData);

        // Mục "Tài khoản" giống Hình 6
        addProfileSectionLabel("Tài khoản");
        LinearLayout accCard = profileWhiteCard();
        addProfileMenuRow(accCard, "🔑", "Đổi mật khẩu", null, 0xff111827, null, true, this::changePassword);
        addProfileMenuRow(accCard, "🚪", "Đăng xuất", null, 0xffdc2626, null, false, this::confirmSignOut);
    }

    /**
     * Trang con "< Chỉnh sửa thông tin" — Giống hệt Hình 5 (ô nhập trắng bo góc + icon bên phải + nút Lưu xanh dương dưới đáy)
     */
    private void profileEditPage() {
        JSONObject p = session.profile();
        addProfileSubHeader("Chỉnh sửa thông tin", () -> {
            profileSubPage = profileEditFromAccountInfo ? "account_info" : "hub";
            profileEditFromAccountInfo = false;
            render();
        });

        // Trường Họ và tên (hiển thị giống Hình 5, chỉ đọc vì do HR quản lý)
        addStyledFieldLabel("Họ và tên", true);
        EditText fullNameEdit = addStyledInputBox(str(p, "fullName"), "👤", InputType.TYPE_CLASS_TEXT, false);

        addStyledFieldLabel("Số điện thoại", false);
        EditText phoneEdit = addStyledInputBox(str(p, "phone"), "📞", InputType.TYPE_CLASS_PHONE, true);

        addStyledFieldLabel("Email", true);
        EditText emailEdit = addStyledInputBox(str(p, "email"), "✉️",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, true);

        addStyledFieldLabel("Địa chỉ liên hệ", false);
        EditText addressEdit = addStyledInputBox(str(p, "address"), "📍",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);

        TextView helperTv = text("Bạn có thể cập nhật Số điện thoại, Email và Địa chỉ. Thông tin nhân sự khác (Họ tên, Phòng ban, Chức vụ) vui lòng liên hệ HR.", 12, 0xff7b8798, false);
        helperTv.setPadding(dp(2), dp(6), dp(2), dp(6));
        body.addView(helperTv);

        TextView notice = text("", 13, 0xffdc2626, false);
        body.addView(notice);

        MaterialButton saveBtn = new MaterialButton(this);
        saveBtn.setText("Lưu");
        saveBtn.setAllCaps(false);
        saveBtn.setTextSize(16);
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setCornerRadius(dp(12));
        saveBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1d61e7));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, dp(52));
        sbp.topMargin = dp(18);
        saveBtn.setLayoutParams(sbp);

        saveBtn.setOnClickListener(v -> {
            if (writing) return;
            String e = emailEdit.getText() == null ? "" : emailEdit.getText().toString().trim();
            String ph = (phoneEdit.getText() == null ? "" : phoneEdit.getText().toString().trim()).replaceAll("[\\s().-]", "");
            String a = addressEdit.getText() == null ? "" : addressEdit.getText().toString().trim();
            if (ph.startsWith("+84")) ph = "0" + ph.substring(3);
            final String normalizedPhone = ph;

            if (e.length() > 254 || (!e.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches())) {
                emailEdit.setError("Email không hợp lệ");
                emailEdit.requestFocus();
                return;
            }
            if (!ph.matches("^(?:0[35789][0-9]{8})?$")) {
                phoneEdit.setError("Nhập số điện thoại Việt Nam gồm 10 chữ số");
                phoneEdit.requestFocus();
                return;
            }
            if (a.length() > 500) {
                addressEdit.setError("Địa chỉ tối đa 500 ký tự");
                addressEdit.requestFocus();
                return;
            }

            phoneEdit.setText(normalizedPhone);
            emailEdit.setEnabled(false);
            phoneEdit.setEnabled(false);
            addressEdit.setEnabled(false);
            saveBtn.setEnabled(false);
            notice.setTextColor(MUTED);
            notice.setText("Đang lưu thay đổi…");
            writing = true;
            String token = session.token();
            executor.execute(() -> {
                try {
                    JSONObject updated = api.send("/api/me/profile", "PUT", token,
                            json("email", e, "phone", normalizedPhone, "address", a));
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        writing = false;
                        session.signIn(token, updated);
                        toast("Đã cập nhật hồ sơ cá nhân.");
                        profileSubPage = profileEditFromAccountInfo ? "account_info" : "hub";
                        profileEditFromAccountInfo = false;
                        render();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        writing = false;
                        emailEdit.setEnabled(true);
                        phoneEdit.setEnabled(true);
                        addressEdit.setEnabled(true);
                        saveBtn.setEnabled(true);
                        notice.setTextColor(0xffdc2626);
                        if (error instanceof HrmApi.ApiException && ((HrmApi.ApiException) error).status == 401) {
                            session.clearSession();
                            signOutLocal();
                            return;
                        }
                        notice.setText(HrmApi.message(error));
                    });
                }
            });
        });

        body.addView(saveBtn);
    }

    private void addStyledFieldLabel(String labelText, boolean required) {
        String full = required ? (labelText + " *") : labelText;
        android.text.SpannableString span = new android.text.SpannableString(full);
        if (required) {
            span.setSpan(new android.text.style.ForegroundColorSpan(0xffdc2626), full.length() - 1, full.length(), 0);
        }
        TextView tv = text("", 15, 0xff111827, true);
        tv.setText(span);
        tv.setPadding(dp(2), dp(10), dp(2), dp(6));
        ((LinearLayout.LayoutParams) tv.getLayoutParams()).bottomMargin = 0;
        body.addView(tv);
    }

    private EditText addStyledInputBox(String initialValue, String rightIcon, int inputType, boolean editable) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = rounded(editable ? Color.WHITE : 0xfff8fafc, 12);
        bg.setStroke(dp(1), 0xffe2e8f0);
        box.setBackground(bg);
        box.setPadding(dp(14), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.bottomMargin = dp(4);
        box.setLayoutParams(bp);

        EditText edit = new EditText(this);
        edit.setText(initialValue);
        edit.setTextSize(15);
        edit.setTextColor(editable ? 0xff111827 : 0xff64748b);
        edit.setBackground(null);
        edit.setInputType(inputType);
        edit.setEnabled(editable);
        edit.setPadding(0, dp(10), dp(8), dp(10));
        box.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));

        if (rightIcon != null && !rightIcon.isEmpty()) {
            View vLine = new View(this);
            vLine.setBackgroundColor(0xffe2e8f0);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(1), dp(22));
            vlp.setMarginEnd(dp(10));
            box.addView(vLine, vlp);

            TextView ic = text(rightIcon, 16, 0xff475569, false);
            ((LinearLayout.LayoutParams) ic.getLayoutParams()).bottomMargin = 0;
            ((LinearLayout.LayoutParams) ic.getLayoutParams()).width = -2;
            box.addView(ic);
        }

        body.addView(box);
        return edit;
    }

    /**
     * Trang con "< Khoản lương / phụ cấp đã công bố" — Giống phong cách Hình 1 & Hình 4
     */
    private void profileSalaryItemsPage() {
        addProfileSubHeader("Khoản lương & Phụ cấp", () -> {
            profileSubPage = "hub";
            render();
        });

        LinearLayout topShortcut = profileWhiteCard();
        addFeatureBadgeRow(topShortcut, "📄", "Mở Phiếu lương cá nhân đã công bố",
                "Xem bảng lương đầy đủ, các khoản phạt đi trễ & 22 dòng ngày công", false, () -> {
                    section = "salary";
                    render();
                });

        LinearLayout container = column();
        body.addView(container);
        loadPublishedSalaryAndAllowances(container);
    }

    /**
     * Trang con "< Hợp đồng và quyết định" — Giống phong cách Hình 1
     */
    private void profileContractsAndDecisionsPage() {
        addProfileSubHeader("Hợp đồng & Quyết định", () -> {
            profileSubPage = "hub";
            render();
        });
        LinearLayout contracts = card("Hợp đồng lao động");
        LinearLayout decisions = card("Quyết định nhân sự đã duyệt");
        loadMyContracts(contracts);
        loadMyDecisions(decisions);
    }

    /**
     * Trang con "< Cài đặt chung" — Giống hệt Hình 3
     */
    private void profileSettingsPage() {
        addProfileSubHeader("Cài đặt chung", () -> {
            profileSubPage = "hub";
            render();
        });

        addProfileSectionLabel("Hiển thị");
        LinearLayout displayCard = profileWhiteCard();
        addProfileMenuRow(displayCard, null, "Chế độ nền tối", null, 0xff111827, "Hệ thống", false,
                () -> toast("Đang sử dụng giao diện sáng tiêu chuẩn theo hệ thống."));

        addProfileSectionLabel("Ngôn ngữ");
        LinearLayout langCard = profileWhiteCard();
        addProfileMenuRow(langCard, null, "Ngôn ngữ", null, 0xff111827, "🇻🇳 Tiếng Việt", false,
                () -> toast("Ngôn ngữ mặc định: Tiếng Việt."));

        addProfileSectionLabel("Đơn vị tiền tệ");
        LinearLayout currCard = profileWhiteCard();
        addProfileMenuRow(currCard, null, "Đơn vị tiền tệ", null, 0xff111827, "₫", false,
                () -> toast("Đơn vị tiền tệ: Đồng Việt Nam (₫)."));

        addProfileSectionLabel("Thông báo");
        LinearLayout notifCard = profileWhiteCard();
        addProfileSwitchRow(notifCard, "Bật âm thanh", profileSoundEnabled, checked -> {
            profileSoundEnabled = checked;
            toast(checked ? "Đã bật âm thanh thông báo." : "Đã tắt âm thanh thông báo.");
        });

        addProfileSectionLabel("Bảo mật");
        LinearLayout secCard = profileWhiteCard();
        addProfileSwitchRow(secCard, "Mã bảo vệ", profileSecurityLockEnabled, checked -> {
            profileSecurityLockEnabled = checked;
            if (checked) {
                changePassword();
            }
        });
    }

    private void addProfileSwitchRow(LinearLayout cardBox, String titleText, boolean initialChecked,
                                     java.util.function.Consumer<Boolean> onChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView lbl = text(titleText, 15, 0xff111827, false);
        ((LinearLayout.LayoutParams) lbl.getLayoutParams()).bottomMargin = 0;
        row.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        sw.setChecked(initialChecked);
        sw.setOnCheckedChangeListener((btn, isChecked) -> {
            if (onChanged != null) onChanged.accept(isChecked);
        });
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        cardBox.addView(row);
    }

    /**
     * Trang con "< Hướng dẫn sử dụng" — Giống hệt Hình 1 (có thanh tìm kiếm + danh sách icon xanh nhạt bên trái)
     */
    private void profileGuidePage() {
        addProfileSubHeader("Hướng dẫn sử dụng", () -> {
            profileSubPage = "hub";
            render();
        });

        // Ô tìm kiếm bo tròn giống Hình 1
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable sbg = rounded(Color.WHITE, 24);
        sbg.setStroke(dp(1), 0xffe2e8f0);
        searchBox.setBackground(sbg);
        searchBox.setPadding(dp(14), dp(2), dp(14), dp(2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.bottomMargin = dp(14);
        searchBox.setLayoutParams(slp);

        TextView sIcon = text("🔍", 15, 0xff94a3b8, false);
        ((LinearLayout.LayoutParams) sIcon.getLayoutParams()).bottomMargin = 0;
        ((LinearLayout.LayoutParams) sIcon.getLayoutParams()).width = -2;
        sIcon.setPadding(0, 0, dp(8), 0);
        searchBox.addView(sIcon);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Tìm tính năng, ví dụ: phiếu lương, chấm công");
        searchInput.setHintTextColor(0xff94a3b8);
        searchInput.setTextSize(14);
        searchInput.setTextColor(0xff111827);
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        searchInput.setText(profileGuideQuery);
        searchInput.setPadding(0, dp(10), 0, dp(10));
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(searchBox);

        LinearLayout listContainer = column();
        body.addView(listContainer);

        final String[][][] guideSections = new String[][][]{
                {
                        {"CHẤM CÔNG", "", "", ""},
                        {"👤", "Tự chấm công cá nhân", "Chấm công vào / ra ca cho chính mình mỗi ngày",
                                "Vào tab Chấm công ở thanh điều hướng dưới cùng để thực hiện Check-in khi bắt đầu ca và Check-out khi kết thúc ca làm việc."},
                        {"⏰", "Quên ra ca / Chấm công bù", "Gửi yêu cầu bổ sung giờ khi quên check-in hoặc check-out",
                                "Khi quên bấm giờ vào hoặc giờ ra, bạn chọn bản ghi công cần bổ sung trong mục Chấm công hoặc Đơn của tôi để gửi đề xuất duyệt."},
                        {"🔎", "Tra soát chấm công & Lịch công", "Xem đi trễ, về sớm, nghỉ phép, ngày lễ theo tháng",
                                "Bấm nút Xem lịch trong mục Chấm công để xem lưới lịch tháng với màu sắc phân biệt ngày đi làm, đi muộn, nghỉ phép và OT."},
                        {"📅", "Xếp ca / Lịch làm việc", "Theo dõi ca làm việc được phân công trong tuần / tháng",
                                "Mở tab Lịch ở thanh dưới cùng để tra cứu toàn bộ ca làm việc được phân công và giờ quy định của từng ca."},
                        {"⚡", "Đăng ký và theo dõi tăng ca (OT)", "Tạo đơn tăng ca, check-out OT và bổ sung giờ OT",
                                "Vào mục Đơn của tôi → chọn tab OT để tạo đơn đăng ký làm thêm giờ hoặc gửi yêu cầu bổ sung giờ OT trong hạn 3 ngày."}
                },
                {
                        {"LƯƠNG & HỒ SƠ", "", "", ""},
                        {"📄", "Phiếu lương cá nhân đã công bố", "Xem thu nhập Gross, khấu trừ, thực lĩnh Net và in PDF",
                                "Khi phòng Nhân sự / Kế toán chốt và công bố lương, bạn mở mục Phiếu lương cá nhân đã công bố để xem chi tiết từng kỳ và lưu bản in PDF."},
                        {"⚠️", "Xem khoản phạt đi trễ & 22 ngày công", "Xem chi tiết trừ đi trễ từng phút và bảng công trong phiếu lương",
                                "Trong Phiếu lương của tôi, bấm vào nút '▼ Xem chi tiết từng ngày công' ngay dưới dòng Lương theo ngày công để xem giờ vào/ra từng ngày và lý do phạt đi trễ."},
                        {"🏦", "Khoản lương / phụ cấp đã công bố", "Tra cứu phụ cấp định mức hợp đồng và lịch sử khoản lương",
                                "Mở mục Khoản lương / phụ cấp đã công bố trong Hồ sơ để xem các khoản phụ cấp đang hưởng và bảng tổng hợp theo từng kỳ lương."},
                        {"✏️", "Cập nhật thông tin liên hệ", "Thay đổi số điện thoại, email, địa chỉ cá nhân",
                                "Vào mục Cá nhân → Chỉnh sửa thông tin để cập nhật số điện thoại (10 chữ số), email và địa chỉ liên hệ của bạn."}
                }
        };

        Runnable renderGuideList = () -> {
            listContainer.removeAllViews();
            String q = profileGuideQuery.trim().toLowerCase(Locale.ROOT);
            for (String[][] sec : guideSections) {
                String secHeader = sec[0][0];
                List<String[]> matched = new ArrayList<>();
                for (int i = 1; i < sec.length; i++) {
                    String[] item = sec[i];
                    if (q.isEmpty() || item[1].toLowerCase(Locale.ROOT).contains(q)
                            || item[2].toLowerCase(Locale.ROOT).contains(q)
                            || item[3].toLowerCase(Locale.ROOT).contains(q)) {
                        matched.add(item);
                    }
                }
                if (matched.isEmpty()) continue;
                TextView sh = text(secHeader, 13, 0xff8c98a9, true);
                sh.setPadding(dp(2), dp(6), dp(2), dp(6));
                ((LinearLayout.LayoutParams) sh.getLayoutParams()).bottomMargin = 0;
                listContainer.addView(sh);

                MaterialCardView card = new MaterialCardView(this);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.bottomMargin = dp(12);
                card.setLayoutParams(cp);
                card.setRadius(dp(16));
                card.setCardElevation(0);
                card.setStrokeColor(0xffedf1f7);
                card.setStrokeWidth(dp(1));
                card.setCardBackgroundColor(Color.WHITE);
                LinearLayout box = column();
                card.addView(box);
                listContainer.addView(card);

                for (int i = 0; i < matched.size(); i++) {
                    String[] item = matched.get(i);
                    addFeatureBadgeRow(box, item[0], item[1], item[2], i + 1 < matched.size(), () -> {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(item[0] + " " + item[1])
                                .setMessage(item[3])
                                .setPositiveButton("Đã hiểu", null)
                                .show();
                    });
                }
            }
        };

        renderGuideList.run();
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                profileGuideQuery = s == null ? "" : s.toString();
                renderGuideList.run();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    /**
     * Trang con "< Góp ý cho ứng dụng" — Giống hệt Hình 2
     */
    private void profileFeedbackPage() {
        addProfileSubHeader("Góp ý cho ứng dụng", () -> {
            profileSubPage = "hub";
            render();
        });

        TextView desc = text(
                "Yêu cầu của bạn sẽ được gửi đến đội ngũ phát triển HRM Center. Chúng tôi sẽ xem xét kỹ lưỡng yêu cầu của bạn và sớm lên kế hoạch tích hợp vào ứng dụng.",
                14, 0xff334155, false);
        desc.setPadding(dp(2), 0, dp(2), dp(8));
        body.addView(desc);

        addStyledFieldLabel("Tiêu đề", true);
        EditText titleEdit = addStyledInputBox("", null, InputType.TYPE_CLASS_TEXT, true);
        titleEdit.setHint("Nhập tiêu đề góp ý");
        titleEdit.setHintTextColor(0xff94a3b8);

        addStyledFieldLabel("Góp ý", true);
        EditText contentEdit = addStyledInputBox("", null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
        contentEdit.setHint("Nội dung góp ý");
        contentEdit.setHintTextColor(0xff94a3b8);
        contentEdit.setMinLines(5);
        contentEdit.setGravity(Gravity.TOP | Gravity.START);

        MaterialButton sendBtn = new MaterialButton(this);
        sendBtn.setText("Gửi");
        sendBtn.setAllCaps(false);
        sendBtn.setTextSize(16);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setCornerRadius(dp(12));
        sendBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1d61e7));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, dp(52));
        sbp.topMargin = dp(24);
        sendBtn.setLayoutParams(sbp);
        sendBtn.setOnClickListener(v -> {
            String t = titleEdit.getText() == null ? "" : titleEdit.getText().toString().trim();
            String c = contentEdit.getText() == null ? "" : contentEdit.getText().toString().trim();
            if (t.isEmpty()) {
                titleEdit.setError("Vui lòng nhập tiêu đề góp ý");
                titleEdit.requestFocus();
                return;
            }
            if (c.isEmpty()) {
                contentEdit.setError("Vui lòng nhập nội dung góp ý");
                contentEdit.requestFocus();
                return;
            }
            toast("Cảm ơn bạn! Góp ý \"" + t + "\" đã được ghi nhận.");
            profileSubPage = "hub";
            render();
        });
        body.addView(sendBtn);
    }

    private void copySupportInfo(JSONObject p) {
        String info = "Mã NV: " + str(p, "employeeCode")
                + " | Họ tên: " + str(p, "fullName")
                + " | Tài khoản: " + str(p, "username")
                + " | Phòng ban: " + str(p, "departmentName");
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("HRM Support Info", info));
            toast("Đã sao chép thông tin hỗ trợ vào bộ nhớ tạm.");
        }
    }

    private void refreshProfileData() {
        if (writing) return;
        loading.setVisibility(View.VISIBLE);
        String token = session.token();
        executor.execute(() -> {
            try {
                JSONObject fresh = api.me(token);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading.setVisibility(View.GONE);
                    session.signIn(token, fresh);
                    toast("Đã làm mới dữ liệu hồ sơ.");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loading.setVisibility(View.GONE);
                    toast(HrmApi.message(e));
                });
            }
        });
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(this).setTitle("Đăng xuất?")
                .setMessage("Kết thúc phiên đăng nhập trên ứng dụng?").setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (d, w) -> {
                    writing = true;
                    executor.execute(() -> {
                        try {
                            api.logout(session.token());
                        } catch (Exception ignored) {
                        }
                        runOnUiThread(() -> {
                            writing = false;
                            session.clearSession();
                            signOutLocal();
                        });
                    });
                }).show();
    }

    /**
     * Tải và hiển thị Khoản lương & Phụ cấp đã công bố ngay bên dưới nút "Khoản lương / phụ cấp đã công bố"
     */
    private void loadPublishedSalaryAndAllowances(LinearLayout container) {
        container.removeAllViews();
        container.setBackground(rounded(0xfff8fafc, 10));
        container.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView loadingTv = text("Đang tải danh sách khoản lương và phụ cấp đã công bố...", 13, MUTED, false);
        container.addView(loadingTv);

        int version = generation;
        String token = session.token();
        executor.execute(() -> {
            JSONArray allowanceArr = new JSONArray();
            JSONArray salaryArr = new JSONArray();
            String errText = null;
            try {
                JSONObject allowRes = api.get("/api/me/allowances", token);
                if (allowRes.optJSONArray("items") != null) {
                    allowanceArr = allowRes.optJSONArray("items");
                }
            } catch (Exception ignored) {
            }
            try {
                JSONObject salRes = api.get("/api/me/salary", token);
                if (salRes.optJSONArray("items") != null) {
                    salaryArr = salRes.optJSONArray("items");
                }
            } catch (Exception e) {
                errText = HrmApi.message(e);
            }
            // Fallback từ /api/payslips/my-payslips nếu /api/me/salary trống
            if (salaryArr == null || salaryArr.length() == 0) {
                try {
                    JSONObject mySlipsRes = api.get("/api/payslips/my-payslips", token);
                    JSONArray slips = mySlipsRes.optJSONArray("items");
                    if (slips != null && slips.length() > 0) {
                        JSONArray built = new JSONArray();
                        for (int i = 0; i < slips.length(); i++) {
                            JSONObject s = slips.getJSONObject(i);
                            String pCode = str(s, "periodCode");
                            JSONArray lines = s.optJSONArray("lines");
                            if (lines != null) {
                                for (int j = 0; j < lines.length(); j++) {
                                    JSONObject l = lines.getJSONObject(j);
                                    JSONObject item = new JSONObject();
                                    item.put("periodCode", pCode);
                                    item.put("componentCode", str(l, "code"));
                                    item.put("componentName", str(l, "name"));
                                    item.put("direction", str(l, "direction"));
                                    item.put("amount", l.optDouble("amount", 0));
                                    item.put("explanation", str(l, "explanation"));
                                    built.put(item);
                                }
                            }
                        }
                        salaryArr = built;
                        errText = null;
                    }
                } catch (Exception ignored) {
                }
            }

            final JSONArray finalAllowances = allowanceArr != null ? allowanceArr : new JSONArray();
            final JSONArray finalSalaries = salaryArr != null ? salaryArr : new JSONArray();
            final String finalErr = errText;

            runOnUiThread(() -> {
                if (isDestroyed() || version != generation) return;
                container.removeAllViews();
                java.util.Locale locale = java.util.Locale.forLanguageTag("vi-VN");
                java.text.NumberFormat money = java.text.NumberFormat.getIntegerInstance(locale);

                // PHẦN 1: PHỤ CẤP ĐỊNH MỨC CÁ NHÂN (THEO HỢP ĐỒNG / CHÍNH SÁCH)
                TextView allowHeader = text("1. Phụ cấp định mức cá nhân đang hưởng", 14, BLUE, true);
                ((LinearLayout.LayoutParams) allowHeader.getLayoutParams()).bottomMargin = dp(6);
                container.addView(allowHeader);

                if (finalAllowances.length() == 0) {
                    container.addView(text("Chưa có phụ cấp cố định riêng hoặc đã gộp trực tiếp trong kỳ lương.", 12, MUTED, false));
                } else {
                    for (int i = 0; i < finalAllowances.length(); i++) {
                        JSONObject a = finalAllowances.optJSONObject(i);
                        if (a == null) continue;
                        LinearLayout aBox = column();
                        aBox.setBackground(rounded(Color.WHITE, 8));
                        aBox.setPadding(dp(10), dp(8), dp(10), dp(8));
                        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
                        ap.bottomMargin = dp(6);
                        aBox.setLayoutParams(ap);

                        LinearLayout top = new LinearLayout(this);
                        top.setOrientation(LinearLayout.HORIZONTAL);
                        String aName = str(a, "typeName").isEmpty() ? str(a, "typeCode") : str(a, "typeName");
                        TextView aTitle = text("🎁 " + aName, 13, INK, true);
                        ((LinearLayout.LayoutParams) aTitle.getLayoutParams()).bottomMargin = 0;
                        double amt = a.optDouble("amount", 0);
                        String amtType = str(a, "amountType");
                        String amtText = "PERCENT_BASE_SALARY".equalsIgnoreCase(amtType)
                                ? (amt + "% lương CB")
                                : ("+" + money.format(amt) + " ₫");
                        TextView aVal = text(amtText, 13, 0xff126049, true);
                        aVal.setGravity(Gravity.END);
                        ((LinearLayout.LayoutParams) aVal.getLayoutParams()).bottomMargin = 0;
                        top.addView(aTitle, new LinearLayout.LayoutParams(0, -2, 1));
                        top.addView(aVal, new LinearLayout.LayoutParams(-2, -2));
                        aBox.addView(top);

                        String basis = "THEO_NGAY_CONG".equalsIgnoreCase(str(a, "calculationBasis"))
                                ? "Tính theo ngày công thực tế"
                                : "Định mức cố định theo kỳ lương";
                        String effFrom = str(a, "effectiveFrom");
                        String effTo = str(a, "effectiveTo");
                        String sub = basis + (effFrom.isEmpty() ? "" : (" · Hiệu lực: " + effFrom + (effTo.isEmpty() ? " → Nay" : (" → " + effTo))));
                        TextView subTv = text(sub, 11, MUTED, false);
                        ((LinearLayout.LayoutParams) subTv.getLayoutParams()).bottomMargin = 0;
                        aBox.addView(subTv);
                        container.addView(aBox);
                    }
                }

                // PHẦN 2: KHOẢN LƯƠNG & PHỤ CẤP ĐÃ CÔNG BỐ THEO KỲ
                TextView salHeader = text("2. Chi tiết khoản lương & phụ cấp đã công bố theo kỳ", 14, BLUE, true);
                salHeader.setPadding(0, dp(8), 0, dp(4));
                container.addView(salHeader);

                if (finalErr != null && finalSalaries.length() == 0) {
                    container.addView(text(finalErr, 13, 0xffa32b32, false));
                    return;
                }
                if (finalSalaries.length() == 0) {
                    container.addView(text("Chưa có khoản lương / phụ cấp nào được công bố.", 13, MUTED, false));
                    return;
                }

                // Gom nhóm theo periodCode
                Map<String, List<JSONObject>> byPeriod = new LinkedHashMap<>();
                for (int i = 0; i < finalSalaries.length(); i++) {
                    JSONObject item = finalSalaries.optJSONObject(i);
                    if (item == null) continue;
                    String pCode = str(item, "periodCode");
                    if (pCode.isEmpty()) pCode = "Kỳ lương";
                    byPeriod.computeIfAbsent(pCode, k -> new ArrayList<>()).add(item);
                }

                List<String> periodKeys = new ArrayList<>(byPeriod.keySet());
                String[] spinnerLabels = new String[periodKeys.size()];
                for (int i = 0; i < periodKeys.size(); i++) {
                    String pk = periodKeys.get(i);
                    spinnerLabels[i] = "Kỳ lương " + pk + " (" + byPeriod.get(pk).size() + " khoản mục)";
                }

                container.addView(text("Chọn kỳ lương để xem các khoản thu nhập, phụ cấp & khấu trừ:", 12, MUTED, false));
                Spinner periodSpinner = new Spinner(this);
                ArrayAdapter<String> spAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerLabels);
                spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                periodSpinner.setAdapter(spAdapter);
                periodSpinner.setBackground(rounded(Color.WHITE, 8));
                periodSpinner.setPadding(dp(8), dp(6), dp(8), dp(6));
                container.addView(periodSpinner, new LinearLayout.LayoutParams(-1, -2));

                LinearLayout periodItemsBox = column();
                periodItemsBox.setPadding(0, dp(8), 0, 0);
                container.addView(periodItemsBox);

                periodSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                        periodItemsBox.removeAllViews();
                    }

                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        periodItemsBox.removeAllViews();
                        if (position < 0 || position >= periodKeys.size()) return;
                        String pk = periodKeys.get(position);
                        List<JSONObject> lines = byPeriod.get(pk);
                        if (lines == null) return;

                        double sumAdd = 0, sumDed = 0;
                        for (JSONObject l : lines) {
                            double amt = l.optDouble("amount", 0);
                            if ("DEDUCT".equalsIgnoreCase(str(l, "direction"))) sumDed += amt;
                            else sumAdd += amt;
                        }

                        LinearLayout summaryRow = column();
                        summaryRow.setBackground(rounded(0xffeef6ff, 8));
                        summaryRow.setPadding(dp(10), dp(8), dp(10), dp(8));
                        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
                        srp.bottomMargin = dp(8);
                        summaryRow.setLayoutParams(srp);
                        summaryRow.addView(text("Tổng hợp kỳ " + pk + ": Thu nhập +" + money.format(sumAdd) + " ₫  |  Khấu trừ −" + money.format(sumDed) + " ₫  |  Thực lĩnh: " + money.format(sumAdd - sumDed) + " ₫", 12, INK, true));
                        periodItemsBox.addView(summaryRow);

                        for (JSONObject l : lines) {
                            boolean isAdd = !"DEDUCT".equalsIgnoreCase(str(l, "direction"));
                            boolean isPenalty = !isAdd && "KHAU_TRU".equalsIgnoreCase(str(l, "componentCode"));
                            LinearLayout itemCard = column();
                            itemCard.setBackground(rounded(isPenalty ? 0xfffff2f2 : Color.WHITE, 8));
                            itemCard.setPadding(dp(10), dp(8), dp(10), dp(8));
                            LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(-1, -2);
                            icp.bottomMargin = dp(6);
                            itemCard.setLayoutParams(icp);

                            LinearLayout top = new LinearLayout(EmployeeActivity.this);
                            top.setOrientation(LinearLayout.HORIZONTAL);
                            String cName = str(l, "componentName");
                            TextView nameView = text((isAdd ? "➕ " : (isPenalty ? "⚠️ " : "➖ ")) + cName, 13, isPenalty ? 0xffa12823 : INK, true);
                            ((LinearLayout.LayoutParams) nameView.getLayoutParams()).bottomMargin = 0;
                            double amt = l.optDouble("amount", 0);
                            TextView amtView = text((isAdd ? "+" : "−") + money.format(amt) + " ₫", 13, isAdd ? 0xff126049 : 0xffa12823, true);
                            amtView.setGravity(Gravity.END);
                            ((LinearLayout.LayoutParams) amtView.getLayoutParams()).bottomMargin = 0;
                            top.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));
                            top.addView(amtView, new LinearLayout.LayoutParams(-2, -2));
                            itemCard.addView(top);

                            String exp = str(l, "explanation");
                            if (!exp.isEmpty()) {
                                TextView expTv = text(exp, 11, MUTED, false);
                                expTv.setPadding(0, dp(3), 0, 0);
                                ((LinearLayout.LayoutParams) expTv.getLayoutParams()).bottomMargin = 0;
                                itemCard.addView(expTv);
                            }
                            periodItemsBox.addView(itemCard);
                        }
                    }
                });
            });
        });
    }

    private void showProfileContracts() {
        profileSubPage = "contracts";
        render();
    }

    private void loadMyContracts(LinearLayout section) {
        LinearLayout results = column();
        section.addView(results);
        loadMyContractPage(results, profileContractPage);
    }

    private void loadMyContractPage(LinearLayout results, int pageIndex) {
        results.removeAllViews();
        results.addView(text("Đang tải hợp đồng…", 13, MUTED, false));
        fetch("/api/me/contracts?page=" + pageIndex + "&size=" + PROFILE_CONTRACT_PAGE_SIZE, data -> {
            JSONArray items = data.getJSONArray("items");
            int totalPages = data.getInt("totalPages");
            if (items.length() == 0 && pageIndex > 0 && pageIndex >= totalPages) {
                profileContractPage = Math.max(0, totalPages - 1);
                loadMyContractPage(results, profileContractPage);
                return;
            }
            profileContractPage = pageIndex;
            results.removeAllViews();
            results.addView(text(data.getLong("totalElements") + " hợp đồng", 12, MUTED, false));
            if (items.length() == 0) {
                results.addView(text("Chưa có dữ liệu hợp đồng lao động.", 13, MUTED, false));
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject contract = items.getJSONObject(i);
                LinearLayout row = personalDocumentRow(results);
                row.addView(text(str(contract, "contractNumber") + " · " + str(contract, "typeName"), 14, INK, true));
                row.addView(text(contractDateRange(contract, "startDate", "endDate") + " · "
                        + contractStatusLabel(str(contract, "status")), 13, MUTED, false));
                long contractId = contract.getLong("contractId");
                button(row, "Xem chi tiết", false, () -> {
                    profileContractId = contractId;
                    profileSubPage = "contract_detail";
                    render();
                });
            }
            if (totalPages > 1) {
                results.addView(text("Trang " + (pageIndex + 1) + " / " + totalPages, 12, MUTED, true));
                if (pageIndex > 0) {
                    button(results, "Trang trước", false, () -> loadMyContractPage(results, pageIndex - 1));
                }
                if (pageIndex + 1 < totalPages) {
                    button(results, "Trang sau", false, () -> loadMyContractPage(results, pageIndex + 1));
                }
            }
        }, message -> documentLoadError(results, message, () -> loadMyContractPage(results, pageIndex)));
    }

    private void loadMyDecisions(LinearLayout section) {
        LinearLayout results = column();
        section.addView(results);
        loadMyDecisionResults(results);
    }

    private void loadMyDecisionResults(LinearLayout results) {
        results.removeAllViews();
        results.addView(text("Đang tải quyết định…", 13, MUTED, false));
        fetch("/api/me/decisions?page=0&size=20", data -> {
            JSONArray items = data.getJSONArray("items");
            results.removeAllViews();
            if (items.length() == 0) {
                results.addView(text("Chưa có quyết định nhân sự đã duyệt.", 13, MUTED, false));
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject decision = items.getJSONObject(i);
                LinearLayout row = personalDocumentRow(results);
                row.addView(text(str(decision, "decisionNumber") + " · " + str(decision, "decisionType"), 13, INK, true));
                row.addView(text(str(decision, "content") + " · " + formatDateVi(str(decision, "issuedDate")), 12, MUTED, false));
            }
        }, message -> documentLoadError(results, message, () -> loadMyDecisionResults(results)));
    }

    private void profileContractDetailPage() {
        addProfileSubHeader("Chi tiết hợp đồng", this::showProfileContracts);
        LinearLayout details = card("Thông tin hợp đồng");
        details.addView(text("Đang tải chi tiết…", 13, MUTED, false));
        fetch("/api/me/contracts/" + profileContractId, data -> {
            JSONObject contract = data.getJSONObject("contract");
            JSONArray terms = data.getJSONArray("salaryTerms");
            details.removeAllViews();
            line(details, "Số hợp đồng", str(contract, "contractNumber"));
            line(details, "Loại hợp đồng", str(contract, "typeName"));
            line(details, "Ngày ký", formatDateVi(str(contract, "signedDate")));
            line(details, "Hiệu lực", contractDateRange(contract, "startDate", "endDate"));
            line(details, "Trạng thái", contractStatusLabel(str(contract, "status")));

            LinearLayout salary = card("Mức lương theo hợp đồng");
            if (terms.length() == 0) {
                salary.addView(text("Chưa có mức lương được ghi nhận cho hợp đồng này.", 13, MUTED, false));
            }
            java.text.NumberFormat money = java.text.NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            money.setMaximumFractionDigits(4);
            for (int i = 0; i < terms.length(); i++) {
                JSONObject term = terms.getJSONObject(i);
                String unit = str(term, "payBasis");
                switch (unit) {
                    case "THANG": unit = "tháng"; break;
                    case "NGAY": unit = "ngày"; break;
                    case "GIO": unit = "giờ"; break;
                    default: break;
                }
                String amount = money.format(new java.math.BigDecimal(term.getString("baseSalary")));
                line(salary, contractDateRange(term, "effectiveFrom", "effectiveTo"),
                        amount + " ₫" + (unit.isEmpty() ? "" : " / " + unit));
            }
        }, message -> documentLoadError(details, message, this::render));
    }

    private LinearLayout personalDocumentRow(LinearLayout parent) {
        LinearLayout row = column();
        row.setBackground(rounded(0xfff8fafc, 8));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        parent.addView(row, params);
        return row;
    }

    private void documentLoadError(LinearLayout panel, String message, Runnable retry) {
        panel.removeAllViews();
        panel.addView(text(message, 13, 0xffa32b32, false));
        button(panel, "Thử lại", false, retry);
    }

    private String contractDateRange(JSONObject row, String fromField, String toField) {
        String end = str(row, toField);
        return formatDateVi(str(row, fromField)) + " → " + (end.isEmpty() ? "Không thời hạn" : formatDateVi(end));
    }

    private String contractStatusLabel(String status) {
        switch (status) {
            case "DA_KY": return "Đã ký";
            case "DA_KET_THUC": return "Đã kết thúc";
            case "DA_HUY": return "Đã hủy";
            default: return status;
        }
    }

    /**
     * Màn hình tra cứu phiếu lương cá nhân của nhân viên trên Mobile
     */
    private void salary() {
        addProfileSubHeader("Phiếu lương của tôi", () -> {
            section = navigation.getSelectedItemId() == R.id.navProfile ? "profile" : "home";
            render();
        });

        fetch("/api/payslips/directory", data -> {
            JSONArray rows = data.optJSONArray("rows");
            if (rows == null || rows.length() == 0) {
                card(null).addView(text("Chưa có phiếu lương được công bố.", 14, MUTED, false));
                return;
            }

            LinearLayout picker = card("Chọn kỳ lương đã công bố");
            String[] labels = new String[rows.length()];
            int initialIndex = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                labels[i] = str(row, "periodCode") + " (v" + row.optInt("versionNo", 1) + ") · " + str(row, "payslipNumber");
                if (selectedPayslipId > 0 && row.optLong("payslipId") == selectedPayslipId) {
                    initialIndex = i;
                }
            }
            Spinner periods = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            periods.setAdapter(adapter);
            if (initialIndex > 0) {
                periods.setSelection(initialIndex);
            }
            picker.addView(periods);
            LinearLayout details = card(null);
            periods.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    details.removeAllViews();
                }

                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    details.removeAllViews();
                    JSONObject row = rows.optJSONObject(position);
                    if (row == null) return;
                    selectedPayslipId = row.optLong("payslipId");
                    fetch("/api/payslips/" + selectedPayslipId, slip -> {
                        if (periods.getSelectedItemPosition() == position) showSalaryDetails(details, row, slip);
                    });
                }
            });
        });
    }

    private void showSalaryDetails(LinearLayout box, JSONObject row, JSONObject slip) throws Exception {
        box.removeAllViews();
        long payslipId = slip.optLong("payslipId", row.optLong("payslipId"));
        java.util.Locale locale = java.util.Locale.forLanguageTag("vi-VN");
        java.text.NumberFormat money = java.text.NumberFormat.getIntegerInstance(locale);
        java.text.NumberFormat days = java.text.NumberFormat.getNumberInstance(locale);
        days.setMaximumFractionDigits(4);
        box.addView(text("Phiếu lương kỳ " + str(slip, "periodCode"), 18, INK, true));
        badge(box, "Đã công bố · " + str(slip, "payslipNumber"), "DA_XAC_NHAN");
        salaryInfoRow(box, "Mã nhân viên", str(row, "employeeCode"),
                "Họ và tên", str(row, "employeeName"));
        salaryInfoRow(box, "Ngày công đi làm", salaryDays(slip, "actualWorkDays", days),
                "Ngày công chuẩn", salaryDays(slip, "standardWorkDays", days));
        String department = str(slip, "departmentName");
        String position = str(slip, "positionName");
        salaryInfoRow(box, "Phòng ban", department.isBlank() ? "Chưa phân công" : department,
                "Chức vụ trong kỳ", position.isBlank() ? "Chưa có dữ liệu" : position);

        double gross = slip.optDouble("totalGross", 0);
        salaryTotal(box, "Tổng thu nhập (Gross)", "+" + money.format(gross), 0xffedf5ff, INK);
        salaryTotal(box, "Tổng khấu trừ & Phạt đi trễ", "−" + money.format(slip.optDouble("totalDeduction", 0)), 0xfffff2ef, 0xffa12823);
        salaryTotal(box, "Thực lĩnh chuyển khoản (Net)", money.format(slip.optDouble("netSalary", 0)), 0xffe7f5ed, 0xff126049);

        button(box, "🖨 Xem bản in Phiếu lương chuẩn / Lưu PDF", false, () -> openPrintablePayslipDialog(payslipId, str(slip, "payslipNumber")));

        JSONArray items = slip.optJSONArray("lines");
        salaryItems(box, items, true, gross, money, locale, payslipId);
        salaryItems(box, items, false, gross, money, locale, payslipId);
    }

    private void openPrintablePayslipDialog(long payslipId, String payslipNumber) {
        fetch("/api/payslips/" + payslipId + "/html-json", data -> {
            String html = str(data, "html");
            if (html.isEmpty()) {
                toast("Không tải được bản in phiếu lương.");
                return;
            }
            android.webkit.WebView webView = new android.webkit.WebView(this);
            webView.getSettings().setJavaScriptEnabled(false);
            webView.getSettings().setAllowFileAccess(false);
            webView.getSettings().setAllowContentAccess(false);
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Bản in " + (payslipNumber.isEmpty() ? ("PS-" + payslipId) : payslipNumber))
                    .setView(webView)
                    .setNegativeButton("Đóng", null)
                    .setPositiveButton("🖨 In / Lưu PDF", (d, w) -> {
                        android.print.PrintManager pm = (android.print.PrintManager) getSystemService( PRINT_SERVICE );
                        if (pm != null) {
                            String jobName = "PhieuLuong_" + (payslipNumber.isEmpty() ? payslipId : payslipNumber);
                            pm.print(jobName, webView.createPrintDocumentAdapter(jobName), new android.print.PrintAttributes.Builder().build());
                        }
                    })
                    .show();
        });
    }

    private void salaryInfoRow(LinearLayout box, String leftLabel, String leftValue,
                               String rightLabel, String rightValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -2, 1);
        leftParams.setMarginEnd(dp(12));
        row.addView(left, leftParams);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        line(left, leftLabel, leftValue);
        line(right, rightLabel, rightValue);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private String salaryDays(JSONObject slip, String key, java.text.NumberFormat format) {
        return slip.isNull(key) ? "Chưa có dữ liệu" : format.format(slip.optDouble(key)) + " ngày";
    }

    private void salaryTotal(LinearLayout box, String label, String amount, int background, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(rounded(background, 10));
        tile.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        tile.addView(text(label, 12, color, true));
        tile.addView(text(amount + " ₫", 18, color, true));
        box.addView(tile, params);
    }

    private void salaryItems(LinearLayout box, JSONArray items, boolean income, double gross,
                             java.text.NumberFormat money, java.util.Locale locale, long payslipId) throws Exception {
        int color = income ? 0xff126049 : 0xffa12823;
        TextView heading = text(
                income ? "1. Các khoản thu nhập, phụ cấp & thưởng (+)"
                       : "2. Các khoản trừ vào lương (Phạt đi trễ từng phút, Cắt cơm trưa, BH & Thuế)",
                15, color, true);
        heading.setPadding(0, dp(16), 0, dp(6));
        box.addView(heading);
        java.text.NumberFormat percent = java.text.NumberFormat.getPercentInstance(locale);
        percent.setMaximumFractionDigits(2);
        boolean found = false;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!(income ? "ADD" : "DEDUCT").equals(str(item, "direction"))) continue;
            found = true;
            double amount = item.optDouble("amount", 0);
            String code = str(item, "code");
            boolean isPenalty = !income && "KHAU_TRU".equalsIgnoreCase(code);
            boolean isWorkSalary = income && "LUONG_CONG".equalsIgnoreCase(code);

            LinearLayout itemCard = column();
            itemCard.setBackground(rounded(isPenalty ? 0xfffff2f2 : (income ? 0xfff6fbf8 : 0xfffafafa), 10));
            itemCard.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(8);
            itemCard.setLayoutParams(cardParams);

            if (isPenalty) {
                badge(itemCard, "⚠️ Khoản trừ đi trễ / về sớm / vi phạm ngày công", "TU_CHOI");
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            TextView label = text(str(item, "name"), 14, isPenalty ? 0xffa12823 : INK, true);
            TextView value = text((income ? "+" : "−") + money.format(amount) + " ₫", 15, color, true);
            value.setGravity(android.view.Gravity.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMarginEnd(dp(8));
            row.addView(label, labelParams);
            row.addView(value, new LinearLayout.LayoutParams(-2, -2));
            itemCard.addView(row, new LinearLayout.LayoutParams(-1, -2));

            String explanation = str(item, "explanation");
            if (!explanation.isEmpty()) {
                itemCard.addView(text(explanation, 12, isPenalty ? 0xff7f1d1d : MUTED, false));
            }
            if (!income && gross > 0) {
                itemCard.addView(text("Tỷ trọng: " + percent.format(amount / gross) + " tổng thu nhập", 12, MUTED, false));
            }

            // Dropdown xem 22-23 dòng ngày công chi tiết ngay dưới dòng Lương theo ngày công (giống Web Admin)
            if (isWorkSalary && payslipId > 0) {
                LinearLayout workDropdownContainer = column();
                workDropdownContainer.setVisibility(View.GONE);
                workDropdownContainer.setPadding(0, dp(8), 0, 0);

                final boolean[] loaded = {false};
                final MaterialButton[] toggleBtn = new MaterialButton[1];
                toggleBtn[0] = button(itemCard, "▼ Xem chi tiết từng ngày công (Check-in/Out, Trễ/Vắng)", false, () -> {
                    boolean open = workDropdownContainer.getVisibility() != View.VISIBLE;
                    workDropdownContainer.setVisibility(open ? View.VISIBLE : View.GONE);
                    if (toggleBtn[0] != null) {
                        toggleBtn[0].setText(open
                                ? "▲ Thu gọn bảng ngày công chi tiết"
                                : "▼ Xem chi tiết từng ngày công (Check-in/Out, Trễ/Vắng)");
                    }
                    if (open && !loaded[0]) {
                        loaded[0] = true;
                        loadPayslipWorkEvidenceDropdown(workDropdownContainer, payslipId);
                    }
                });
                itemCard.addView(workDropdownContainer);
            }

            box.addView(itemCard);
        }
        if (!found) box.addView(text("Không có khoản mục.", 12, MUTED, false));
    }

    private void loadPayslipWorkEvidenceDropdown(LinearLayout container, long payslipId) {
        container.removeAllViews();
        container.addView(text("Đang tải minh chứng ngày công (Giờ Check-in/Out, Đi trễ, Vắng mặt)...", 12, MUTED, false));
        fetch("/api/payslips/" + payslipId + "/work-details", data -> {
            container.removeAllViews();
            long lateMins = data.optLong("totalLateMinutes", 0);
            int forfeitDays = data.optInt("forfeitedLunchDays", 0);
            double unexcused = data.optDouble("unexcusedAbsenceDays", 0);
            double actualDays = data.optDouble("actualWorkDays", 0);
            double stdDays = data.optDouble("standardWorkDays", 22);
            double totalHours = data.optDouble("totalWorkedHours", 0);

            LinearLayout summaryBox = column();
            summaryBox.setBackground(rounded(0xffedf5ff, 8));
            summaryBox.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, -2);
            sbp.bottomMargin = dp(8);
            summaryBox.setLayoutParams(sbp);
            summaryBox.addView(text(
                    "📅 Tổng hợp công: Đi làm " + actualDays + "/" + stdDays + " ngày · Tổng giờ: " + totalHours + "h"
                    + "\n⏰ Đi trễ / Về sớm: " + lateMins + " phút"
                    + (forfeitDays > 0 ? (" · 🚫 Cắt cơm trưa: " + forfeitDays + " ngày") : "")
                    + (unexcused > 0 ? (" · ❌ Vắng không phép: " + unexcused + " ngày") : ""),
                    12, INK, true));
            container.addView(summaryBox);

            JSONArray records = data.optJSONArray("dailyRecords");
            if (records == null || records.length() == 0) {
                container.addView(text("Không có dữ liệu lịch làm việc trong kỳ này.", 12, MUTED, false));
                return;
            }
            for (int i = 0; i < records.length(); i++) {
                JSONObject day = records.optJSONObject(i);
                if (day == null) continue;
                long dLate = day.optLong("lateMinutes", 0);
                long dEarly = day.optLong("earlyLeaveMinutes", 0);
                boolean lunchCut = day.optBoolean("lunchAllowanceForfeited", false);
                String res = str(day, "attendanceResult");
                boolean isAbsent = "VANG_KHONG_PHEP".equals(res);
                boolean hasViolation = dLate > 0 || dEarly > 0 || lunchCut || isAbsent;

                LinearLayout dCard = column();
                dCard.setBackground(rounded(
                        (lunchCut || isAbsent) ? 0xfffff1f2 : (hasViolation ? 0xfffff8e6 : Color.WHITE), 8));
                dCard.setPadding(dp(10), dp(8), dp(10), dp(8));
                LinearLayout.LayoutParams dcp = new LinearLayout.LayoutParams(-1, -2);
                dcp.bottomMargin = dp(6);
                dCard.setLayoutParams(dcp);

                String statusLabel;
                if (isAbsent) statusLabel = "❌ Vắng không phép";
                else if ("NGHI_PHEP_CO_LUONG".equals(res)) statusLabel = "📝 Nghỉ phép có lương";
                else if (lunchCut) statusLabel = "🚫 Trễ " + dLate + "p (>1/2 ca · Cắt cơm)";
                else if (dLate > 0 && dEarly > 0) statusLabel = "⏰ Trễ " + dLate + "p · Sớm " + dEarly + "p";
                else if (dLate > 0) statusLabel = "⏰ Đi trễ " + dLate + " phút";
                else if (dEarly > 0) statusLabel = "⏰ Về sớm " + dEarly + " phút";
                else if ("DI_LAM".equals(res)) statusLabel = "✔ Đúng giờ";
                else statusLabel = "Chưa chấm công";

                LinearLayout headerRow = new LinearLayout(this);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                TextView dateTv = text("#" + (i + 1) + " · " + str(day, "workDate"), 13, INK, true);
                ((LinearLayout.LayoutParams) dateTv.getLayoutParams()).bottomMargin = 0;
                TextView stTv = text(statusLabel, 12,
                        (lunchCut || isAbsent) ? 0xffa12823 : (hasViolation ? 0xff9a5b00 : 0xff126049), true);
                stTv.setGravity(Gravity.END);
                ((LinearLayout.LayoutParams) stTv.getLayoutParams()).bottomMargin = 0;
                headerRow.addView(dateTv, new LinearLayout.LayoutParams(0, -2, 1));
                headerRow.addView(stTv, new LinearLayout.LayoutParams(-2, -2));
                dCard.addView(headerRow);

                String aStart = shortTime(str(day, "actualStart"));
                String aEnd = shortTime(str(day, "actualEnd"));
                double wHrs = day.optDouble("workedHours", 0);
                dCard.addView(text("Check-in/Out: " + aStart + " - " + aEnd + "  ·  Giờ làm: " + wHrs + "h", 12, INK, false));

                String note = str(day, "evidenceNote");
                if (!note.isEmpty()) {
                    TextView noteTv = text(note, 11, MUTED, false);
                    ((LinearLayout.LayoutParams) noteTv.getLayoutParams()).bottomMargin = 0;
                    dCard.addView(noteTv);
                }
                container.addView(dCard);
            }
        });
    }

    private String shortTime(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        int tIdx = raw.indexOf('T');
        String timePart = tIdx >= 0 ? raw.substring(tIdx + 1) : raw;
        return timePart.length() >= 5 ? timePart.substring(0, 5) : timePart;
    }

    private void editProfile() {
        if (writing) return;
        JSONObject p = session.profile();
        LinearLayout box = form();
        TextInputEditText email = input(box, "Email", str(p, "email"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        TextInputEditText phone = input(box, "Số điện thoại", str(p, "phone"), InputType.TYPE_CLASS_PHONE);
        TextInputEditText address = input(box, "Địa chỉ", str(p, "address"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView notice = text("", 13, 0xffa32b32, false);
        box.addView(notice);
        dialog("Chỉnh sửa hồ sơ cá nhân", box, "Lưu thay đổi", d -> {
            if (writing) return;
            String e = value(email), ph = value(phone).replaceAll("[\\s().-]", ""), a = value(address);
            if (ph.startsWith("+84")) ph = "0" + ph.substring(3);
            final String normalizedPhone = ph;
            if (e.length() > 254 || (!e.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches())) {
                email.setError("Email không hợp lệ"); email.requestFocus(); return;
            }
            if (!ph.matches("^(?:0[35789][0-9]{8})?$")) {
                phone.setError("Nhập số điện thoại Việt Nam gồm 10 chữ số"); phone.requestFocus(); return;
            }
            if (a.length() > 500) {
                address.setError("Địa chỉ tối đa 500 ký tự"); address.requestFocus(); return;
            }
            phone.setText(normalizedPhone);
            email.setEnabled(false); phone.setEnabled(false); address.setEnabled(false);
            notice.setTextColor(MUTED);
            notice.setText("Đang lưu thay đổi…");
            writing = true;
            d.setCancelable(false);
            d.getButton(-1).setEnabled(false);
            d.getButton(-2).setEnabled(false);
            String token = session.token();
            executor.execute(() -> {
                try {
                    JSONObject updated = api.send("/api/me/profile", "PUT", token,
                            json("email", e, "phone", normalizedPhone, "address", a));
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        writing = false;
                        session.signIn(token, updated);
                        d.dismiss();
                        toast("Đã cập nhật hồ sơ cá nhân.");
                        render();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        writing = false;
                        d.setCancelable(true);
                        email.setEnabled(true); phone.setEnabled(true); address.setEnabled(true);
                        notice.setTextColor(0xffa32b32);
                        d.getButton(-1).setEnabled(true);
                        d.getButton(-2).setEnabled(true);
                        if (error instanceof HrmApi.ApiException && ((HrmApi.ApiException) error).status == 401) {
                            d.dismiss(); session.clearSession(); signOutLocal(); return;
                        }
                        notice.setText(HrmApi.message(error));
                        notice.postDelayed(() -> notice.setText(""), 10000);
                    });
                }
            });
        });
    }

    private void changePassword() {
        LinearLayout box = form();
        TextInputEditText old = input(box, "Mật khẩu hiện tại", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD),
                next = input(box, "Mật khẩu mới (8–72 ký tự)", "",
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD),
                confirm = input(box, "Xác nhận mật khẩu mới", "",
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialog("Đổi mật khẩu", box, "Lưu mật khẩu", d -> {
            String a = old.getText() == null ? "" : old.getText().toString(),
                    b = next.getText() == null ? "" : next.getText().toString(),
                    c = confirm.getText() == null ? "" : confirm.getText().toString();
            if (a.isEmpty() || b.length() < 8 || b.length() > 72 || !b.equals(c)) {
                confirm.setError("Nhập mật khẩu hợp lệ và xác nhận trùng khớp.");
                return;
            }
            writing = true;
            executor.execute(() -> {
                try {
                    api.send("/api/auth/change-password", "POST", session.token(),
                            json("currentPassword", a, "newPassword", b, "confirmPassword", c));
                    runOnUiThread(() -> {
                        if (isDestroyed())
                            return;
                        writing = false;
                        d.dismiss();
                        session.clearSession();
                        toast("Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
                        signOutLocal();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        writing = false;
                        toast(HrmApi.message(e));
                    });
                }
            });
        });
    }

    private void signOutLocal() {
        session = (HrmApplication) getApplication();
        session.clearSession();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override
    protected void onDestroy() {
        ++generation;
        if(notifications!=null)notifications.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }
}
