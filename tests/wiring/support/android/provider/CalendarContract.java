package android.provider; import android.net.Uri;
public final class CalendarContract {
 public static final class Calendars { public static final Uri CONTENT_URI=Uri.parse("content://calendar/calendars"); public static final String _ID="_id",CALENDAR_DISPLAY_NAME="calendar_displayName",ACCOUNT_NAME="account_name",ACCOUNT_TYPE="account_type",IS_PRIMARY="isPrimary",CALENDAR_ACCESS_LEVEL="calendar_access_level",VISIBLE="visible"; public static final int CAL_ACCESS_CONTRIBUTOR=500; }
 public static final class Events { public static final Uri CONTENT_URI=Uri.parse("content://calendar/events"); public static final String _ID="_id",CALENDAR_ID="calendar_id",TITLE="title",DESCRIPTION="description",DTSTART="dtstart",DTEND="dtend",EVENT_TIMEZONE="eventTimezone",AVAILABILITY="availability",CUSTOM_APP_PACKAGE="customAppPackage",CUSTOM_APP_URI="customAppUri"; public static final int AVAILABILITY_BUSY=0; }
}
