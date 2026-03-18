#include "jinix.h"
#include "string.h"

jint PostfixAddIntField(JNIEnv *env, jobject obj, jfieldID fieldID, jint change) {
    jint value = env->GetIntField(obj, fieldID);
    env->SetIntField(obj, fieldID, value + change);
    return value;
}

// --- GLOBAL JNI OBJECTS ---
jclass class_org_jinix_Main;
jfieldID org_jinix_Main_NUMBER;
jfieldID org_jinix_Main_counter;
jmethodID org_jinix_Main_giveNumber_II;
jclass class_java_lang_Integer;
jmethodID java_lang_Integer_toString_I;
jclass class_java_lang_System;
jfieldID java_lang_System_out;
jclass class_java_io_PrintStream;
jmethodID java_io_PrintStream_println_Ljava_lang_String;

void Java_org_jinix_Jinix_init(JNIEnv *env) {
    class_org_jinix_Main = env->FindClass("org/jinix/Main");
    org_jinix_Main_NUMBER = env->GetStaticFieldID(class_org_jinix_Main, "NUMBER", "I");
    org_jinix_Main_counter = env->GetFieldID(class_org_jinix_Main, "counter", "I");
    org_jinix_Main_giveNumber_II = env->GetMethodID(class_org_jinix_Main, "giveNumber", "(II)I");
    class_java_lang_Integer = env->FindClass("java/lang/Integer");
    java_lang_Integer_toString_I = env->GetStaticMethodID(class_java_lang_Integer, "toString", "(I)Ljava/lang/String;");
    class_java_lang_System = env->FindClass("java/lang/System");
    java_lang_System_out = env->GetStaticFieldID(class_java_lang_System, "out", "Ljava/io/PrintStream;");
    class_java_io_PrintStream = env->FindClass("java/io/PrintStream");
    java_io_PrintStream_println_Ljava_lang_String = env->GetMethodID(class_java_io_PrintStream, "println", "(Ljava/lang/String;)V");
}

jint Java_org_jinix_Main_testNative(JNIEnv *env, jobject thisObject, jint n) {
    auto sum = 0;
    std::string str = "";
    for (int i = 0; i < n + (int)env->GetStaticIntField(class_org_jinix_Main, org_jinix_Main_NUMBER); i++) {
        sum += (int)env->CallIntMethod(thisObject, org_jinix_Main_giveNumber_II, i, (int)PostfixAddIntField(env, thisObject, org_jinix_Main_counter, 1));
        str += env->CallStaticObjectMethod(class_java_lang_Integer, java_lang_Integer_toString_I, i);
    }
    env->CallVoidMethod(env->GetStaticObjectField(class_java_lang_System, java_lang_System_out), java_io_PrintStream_println_Ljava_lang_String, str);
    return sum;
}


