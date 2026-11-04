package com.hrm.employee;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class EmployeeNavigationTest {
 @Test public void personalNavigationUsesOwnDataAndNoManagementActions()throws Exception{
  String token=InstrumentationRegistry.getArguments().getString("employeeToken");Assume.assumeNotNull(token);
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  org.json.JSONObject profile=new HrmApi(context.getString(R.string.api_base_url)).me(token);assertEquals("EMPLOYEE",profile.getString("role"));
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((HrmApplication)context.getApplicationContext()).signIn(token,profile));
  try(ActivityScenario<EmployeeActivity> scenario=ActivityScenario.launch(EmployeeActivity.class)){
   waitIdle(scenario);onView(withText("Không gian cá nhân")).check(matches(isDisplayed()));capture(context,"employee-home.png");
   scenario.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navSchedule));waitIdle(scenario);onView(withText("LỊCH CỦA BẠN")).check(matches(isDisplayed()));capture(context,"employee-schedule.png");
   scenario.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navAttendance));waitIdle(scenario);onView(withText("Chấm công của tôi")).check(matches(isDisplayed()));capture(context,"employee-attendance.png");
   scenario.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navRequests));waitIdle(scenario);scenario.onActivity(a->assertEquals("Đơn của tôi",((android.widget.TextView)((android.widget.LinearLayout)a.findViewById(R.id.employeeBody)).getChildAt(0)).getText().toString()));onView(withText("Duyệt đơn")).check(doesNotExist());capture(context,"employee-requests.png");
   onView(withText("Đơn OT")).perform(click());waitIdle(scenario);capture(context,"employee-overtime.png");
   scenario.onActivity(a->((com.google.android.material.bottomnavigation.BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navProfile));waitIdle(scenario);onView(withText("Hồ sơ của tôi")).check(matches(isDisplayed()));onView(withText("Sửa hồ sơ")).check(doesNotExist());capture(context,"employee-profile.png");
  }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((HrmApplication)context.getApplicationContext()).clearSession());}
 }
 private void waitIdle(ActivityScenario<EmployeeActivity> scenario){long deadline=SystemClock.elapsedRealtime()+30000;AtomicBoolean ready=new AtomicBoolean();while(SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->ready.set(a.findViewById(R.id.employeeLoading).getVisibility()!=View.VISIBLE));if(ready.get())return;SystemClock.sleep(100);}fail("Mobile API response timeout");}
 private void capture(android.content.Context c,String name)throws Exception{Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
