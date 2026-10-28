# kotlinx.serialization: mantém os serializers gerados para os modelos e rotas.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rivaisdolar.app.**$$serializer { *; }
-keepclassmembers class com.rivaisdolar.app.** { *** Companion; }
-keepclasseswithmembers class com.rivaisdolar.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor / OkHttp referenciam classes opcionais que não existem no Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.ktor.util.debug.**
