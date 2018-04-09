package com.eric.polygonsdemo;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.hamcrest.Matcher;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
public class MapsActivityTest {
    @Rule
    public ActivityTestRule<MapsActivity> activityTestRule = new ActivityTestRule<>(MapsActivity
            .class);

    @Test
    public void test() {
        Matcher<View> matcher = withText(R.string.cta_show_polygons);
        onView(matcher).perform(click());
    }
}
