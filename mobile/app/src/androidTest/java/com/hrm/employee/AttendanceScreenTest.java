package com.hrm.employee;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class AttendanceScreenTest {
 @Test public void weekDayNavigationAndRealScheduleRendering()throws Exception {
  String token=InstrumentationRegistry.getArguments().getString("employeeToken");Assume.assumeNotNull(token);
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();HrmApi api=new HrmApi(context.getString(R.string.api_base_url));JSONObject profile=api.me(token);assertEquals("EMPLOYEE",profile.getString("role"));InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((HrmApplication)context.getApplicationContext()).signIn(token,profile));
  try(ActivityScenario<EmployeeActivity> scenario=ActivityScenario.launch(EmployeeActivity.class)){
   scenario.onActivity(a->((BottomNavigationView)a.findViewById(R.id.employeeNavigation)).setSelectedItemId(R.id.navAttendance));idle(scenario);capture(scenario,context,"attendance-current-week.png");
   scenario.onActivity(a->{try{SchedulePeriod period=period(a);LocalDate before=period.first();a.findViewById(R.id.main).findViewWithTag("attendance.next").performClick();assertEquals(before.plusWeeks(1),period.first());}catch(Exception e){throw new IllegalStateException(e);}});idle(scenario);
   scenario.onActivity(a->{try{a.findViewById(R.id.main).findViewWithTag("attendance.day").performClick();assertFalse(period(a).weekly());assertEquals(period(a).first(),period(a).last());}catch(Exception e){throw new IllegalStateException(e);}});idle(scenario);capture(scenario,context,"attendance-day.png");
   LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));JSONObject data=api.get("/api/attendance-workspace/records?mine=true&from="+today.minusDays(90)+"&to="+today+"&page=0",token);JSONArray rows=data.optJSONArray("items");if(rows!=null&&rows.length()>0){LocalDate day=LocalDate.parse(rows.getJSONObject(0).getString("workDate"));scenario.onActivity(a->{try{period(a).weekly(true);period(a).select(day);Method render=EmployeeActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);}catch(Exception e){throw new IllegalStateException(e);}});idle(scenario);capture(scenario,context,"attendance-with-data.png");}
  }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->((HrmApplication)context.getApplicationContext()).clearSession());}
 }
 private SchedulePeriod period(EmployeeActivity a)throws Exception{Field field=EmployeeActivity.class.getDeclaredField("attendancePeriod");field.setAccessible(true);return (SchedulePeriod)field.get(a);}
 private void idle(ActivityScenario<EmployeeActivity> scenario){long end=SystemClock.elapsedRealtime()+30000;AtomicBoolean ready=new AtomicBoolean();while(SystemClock.elapsedRealtime()<end){scenario.onActivity(a->ready.set(a.findViewById(R.id.employeeLoading).getVisibility()!=View.VISIBLE&&!a.findViewById(R.id.main).isLayoutRequested()));if(ready.get())return;SystemClock.sleep(100);}fail("Calendar did not finish loading");}
 private void capture(ActivityScenario<EmployeeActivity> scenario,android.content.Context context,String name){scenario.onActivity(a->{View root=a.findViewById(R.id.main);Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new IllegalStateException(e);}finally{image.recycle();}});}
}
