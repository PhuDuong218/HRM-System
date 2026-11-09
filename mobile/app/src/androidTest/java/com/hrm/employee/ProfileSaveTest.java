package com.hrm.employee;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ProfileSaveTest {
 @Test public void savesContactFromProfileFormAndPersists() throws Exception {
  var instrumentation=InstrumentationRegistry.getInstrumentation();
  var context=instrumentation.getTargetContext();
  HrmApi api=new HrmApi(context.getString(R.string.api_base_url));
  JSONObject login=api.login("sample.emp001","Demo@2026!");
  String token=login.optString("token",login.optString("accessToken"));
  JSONObject original=api.me(token);
  HrmApplication app=(HrmApplication)context.getApplicationContext();
  instrumentation.runOnMainSync(()->app.signIn(token,original));
  String changed="Dia chi kiem thu luu ho so 2026";
  try(ActivityScenario<EmployeeActivity> scenario=ActivityScenario.launch(EmployeeActivity.class)) {
   scenario.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navProfile));
   onView(withText("Chỉnh sửa hồ sơ")).perform(scrollTo(),click());
   onView(withHint("Số điện thoại")).perform(replaceText("+84 901 234 567"),closeSoftKeyboard());
   onView(withHint("Địa chỉ")).perform(replaceText(changed),closeSoftKeyboard());
   onView(withId(android.R.id.button1)).perform(click());
   AtomicBoolean done=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+20000;
   while(SystemClock.elapsedRealtime()<deadline){instrumentation.runOnMainSync(()->done.set(changed.equals(app.profile().optString("address"))));if(done.get())break;SystemClock.sleep(150);}
   assertTrue("Profile form did not save",done.get());
   assertEquals(changed,api.me(token).optString("address"));
   assertEquals("0901234567",api.me(token).optString("phone"));
  } finally {
   api.send("/api/me/profile","PUT",token,new JSONObject().put("email",original.optString("email","")).put("phone",original.isNull("phone") ? "" : original.optString("phone","")).put("address",original.optString("address","")));
  }
 }
}
