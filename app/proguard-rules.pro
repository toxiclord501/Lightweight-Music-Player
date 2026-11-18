# Add project-specific ProGuard/R8 keep rules here.
# These are applied on top of the bundled defaults (proguard-android-optimize.txt) when the
# build type has minifyEnabled = true.
#
# Keep a class that is referenced only by reflection / from XML, e.g.:
# -keep class com.example.SomeClass { *; }
#
# Preserve line numbers for readable crash stack traces, then hide the original file name:
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# Stack trace crash yang bisa dibaca (nama file + nomor baris) - dipakai oleh
# "Laporan crash terakhir" di Setting.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Kode app sendiri kecil; dijaga utuh supaya R8 tidak mengacak/membuang kelas yang
# dipakai lewat manifest (service), flow antar-komponen, dan enum (values()).
-keep class com.txc.mp.** { *; }
-keepclassmembers enum com.txc.mp.** { *; }
