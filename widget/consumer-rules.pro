# Glance creates ActionCallback classes by reflection from the class name stored in the widget's
# PendingIntent (actionRunCallback<T>()). Without this, R8 removes their no-arg constructors and
# every Quick-Sort widget button fails in release builds with NoSuchMethodException (ADR-0031).
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
