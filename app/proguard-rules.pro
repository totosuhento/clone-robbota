# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
# Accessibility service dipanggil sistem lewat nama kelas
-keep class com.robotta.automation.MarketAutomationService
