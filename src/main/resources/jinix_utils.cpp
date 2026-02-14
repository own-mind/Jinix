// ------- JINIX UTILITIES -------
// Code below is used only by Jinix transpiler to extend JNI functionality and should be used in any other way
// It is assumed that jni libraries jni.h has been already included in the code above

jobject SetAndGetObjectField(JNIEnv *env, jobject obj, jfieldID fieldID, jobject value) {
    env->SetObjectField(obj, fieldID, value);
    return value;
}

jboolean SetAndGetBooleanField(JNIEnv *env, jobject obj, jfieldID fieldID, jboolean value) {
    env->SetBooleanField(obj, fieldID, value);
    return value;
}

jbyte SetAndGetByteField(JNIEnv *env, jobject obj, jfieldID fieldID, jbyte value) {
    env->SetByteField(obj, fieldID, value);
    return value;
}

jchar SetAndGetCharField(JNIEnv *env, jobject obj, jfieldID fieldID, jchar value) {
    env->SetCharField(obj, fieldID, value);
    return value;
}

jshort SetAndGetShortField(JNIEnv *env, jobject obj, jfieldID fieldID, jshort value) {
    env->SetShortField(obj, fieldID, value);
    return value;
}

jint SetAndGetIntField(JNIEnv *env, jobject obj, jfieldID fieldID, jint value) {
    env->SetIntField(obj, fieldID, value);
    return value;
}

jlong SetAndGetLongField(JNIEnv *env, jobject obj, jfieldID fieldID, jlong value) {
    env->SetLongField(obj, fieldID, value);
    return value;
}

jfloat SetAndGetFloatField(JNIEnv *env, jobject obj, jfieldID fieldID, jfloat value) {
    env->SetFloatField(obj, fieldID, value);
    return value;
}

jdouble SetAndGetDoubleField(JNIEnv *env, jobject obj, jfieldID fieldID, jdouble value) {
    env->SetDoubleField(obj, fieldID, value);
    return value;
}

// -------- PREFIX & POSTFIX ---------

jbyte PostfixAddByteField(JNIEnv *env, jobject obj, jfieldID fieldID, jbyte change) {
    jbyte value = env->GetByteField(obj, fieldID);
    env->SetByteField(obj, fieldID, value + change);
    return value;
}

jbyte PrefixAddByteField(JNIEnv *env, jobject obj, jfieldID fieldID, jbyte change) {
    return SetAndGetByteField(env, obj, fieldID, env->GetByteField(obj, fieldID) + change);
}

jchar PostfixAddCharField(JNIEnv *env, jobject obj, jfieldID fieldID, jchar change) {
    jchar value = env->GetCharField(obj, fieldID);
    env->SetCharField(obj, fieldID, value + change);
    return value;
}

jchar PrefixAddCharField(JNIEnv *env, jobject obj, jfieldID fieldID, jchar change) {
    return SetAndGetCharField(env, obj, fieldID, env->GetCharField(obj, fieldID) + change);
}

jshort PostfixAddShortField(JNIEnv *env, jobject obj, jfieldID fieldID, jshort change) {
    jshort value = env->GetShortField(obj, fieldID);
    env->SetShortField(obj, fieldID, value + change);
    return value;
}

jshort PrefixAddShortField(JNIEnv *env, jobject obj, jfieldID fieldID, jshort change) {
    return SetAndGetShortField(env, obj, fieldID, env->GetShortField(obj, fieldID) + change);
}

jint PostfixAddIntField(JNIEnv *env, jobject obj, jfieldID fieldID, jint change) {
    jint value = env->GetIntField(obj, fieldID);
    env->SetIntField(obj, fieldID, value + change);
    return value;
}

jint PrefixAddIntField(JNIEnv *env, jobject obj, jfieldID fieldID, jint change) {
    return SetAndGetIntField(env, obj, fieldID, env->GetIntField(obj, fieldID) + change);
}

jlong PostfixAddLongField(JNIEnv *env, jobject obj, jfieldID fieldID, jlong change) {
    jlong value = env->GetLongField(obj, fieldID);
    env->SetLongField(obj, fieldID, value + change);
    return value;
}

jlong PrefixAddLongField(JNIEnv *env, jobject obj, jfieldID fieldID, jlong change) {
    return SetAndGetLongField(env, obj, fieldID, env->GetLongField(obj, fieldID) + change);
}

jfloat PostfixAddFloatField(JNIEnv *env, jobject obj, jfieldID fieldID, jfloat change) {
    jfloat value = env->GetFloatField(obj, fieldID);
    env->SetFloatField(obj, fieldID, value + change);
    return value;
}

jfloat PrefixAddFloatField(JNIEnv *env, jobject obj, jfieldID fieldID, jfloat change) {
    return SetAndGetFloatField(env, obj, fieldID, env->GetFloatField(obj, fieldID) + change);
}

jdouble PostfixAddDoubleField(JNIEnv *env, jobject obj, jfieldID fieldID, jdouble change) {
    jdouble value = env->GetDoubleField(obj, fieldID);
    env->SetDoubleField(obj, fieldID, value + change);
    return value;
}

jdouble PrefixAddDoubleField(JNIEnv *env, jobject obj, jfieldID fieldID, jdouble change) {
    return SetAndGetDoubleField(env, obj, fieldID, env->GetDoubleField(obj, fieldID) + change);
}

// ---------- STATIC FIELDS ----------

jobject SetAndGetStaticObjectField(JNIEnv *env, jclass clazz, jfieldID fieldID, jobject value) {
    env->SetStaticObjectField(clazz, fieldID, value);
    return value;
}

jboolean SetAndGetStaticBooleanField(JNIEnv *env, jclass clazz, jfieldID fieldID, jboolean value) {
    env->SetStaticBooleanField(clazz, fieldID, value);
    return value;
}

jbyte SetAndGetStaticByteField(JNIEnv *env, jclass clazz, jfieldID fieldID, jbyte value) {
    env->SetStaticByteField(clazz, fieldID, value);
    return value;
}

jchar SetAndGetStaticCharField(JNIEnv *env, jclass clazz, jfieldID fieldID, jchar value) {
    env->SetStaticCharField(clazz, fieldID, value);
    return value;
}

jshort SetAndGetStaticShortField(JNIEnv *env, jclass clazz, jfieldID fieldID, jshort value) {
    env->SetStaticShortField(clazz, fieldID, value);
    return value;
}

jint SetAndGetStaticIntField(JNIEnv *env, jclass clazz, jfieldID fieldID, jint value) {
    env->SetStaticIntField(clazz, fieldID, value);
    return value;
}

jlong SetAndGetStaticLongField(JNIEnv *env, jclass clazz, jfieldID fieldID, jlong value) {
    env->SetStaticLongField(clazz, fieldID, value);
    return value;
}

jfloat SetAndGetStaticFloatField(JNIEnv *env, jclass clazz, jfieldID fieldID, jfloat value) {
    env->SetStaticFloatField(clazz, fieldID, value);
    return value;
}

jdouble SetAndGetStaticDoubleField(JNIEnv *env, jclass clazz, jfieldID fieldID, jdouble value) {
    env->SetStaticDoubleField(clazz, fieldID, value);
    return value;
}

// -------- PREFIX & POSTFIX ---------

jbyte PostfixAddStaticByteField(JNIEnv *env, jclass clazz, jfieldID fieldID, jbyte change) {
    jbyte value = env->GetStaticByteField(clazz, fieldID);
    env->SetStaticByteField(clazz, fieldID, value + change);
    return value;
}

jbyte PrefixAddStaticByteField(JNIEnv *env, jclass clazz, jfieldID fieldID, jbyte change) {
    return SetAndGetStaticByteField(env, clazz, fieldID, env->GetStaticByteField(clazz, fieldID) + change);
}

jchar PostfixAddStaticCharField(JNIEnv *env, jclass clazz, jfieldID fieldID, jchar change) {
    jchar value = env->GetStaticCharField(clazz, fieldID);
    env->SetStaticCharField(clazz, fieldID, value + change);
    return value;
}

jchar PrefixAddStaticCharField(JNIEnv *env, jclass clazz, jfieldID fieldID, jchar change) {
    return SetAndGetStaticCharField(env, clazz, fieldID, env->GetStaticCharField(clazz, fieldID) + change);
}

jshort PostfixAddStaticShortField(JNIEnv *env, jclass clazz, jfieldID fieldID, jshort change) {
    jshort value = env->GetStaticShortField(clazz, fieldID);
    env->SetStaticShortField(clazz, fieldID, value + change);
    return value;
}

jshort PrefixAddStaticShortField(JNIEnv *env, jclass clazz, jfieldID fieldID, jshort change) {
    return SetAndGetStaticShortField(env, clazz, fieldID, env->GetStaticShortField(clazz, fieldID) + change);
}

jint PostfixAddStaticIntField(JNIEnv *env, jclass clazz, jfieldID fieldID, jint change) {
    jint value = env->GetStaticIntField(clazz, fieldID);
    env->SetStaticIntField(clazz, fieldID, value + change);
    return value;
}

jint PrefixAddStaticIntField(JNIEnv *env, jclass clazz, jfieldID fieldID, jint change) {
    return SetAndGetStaticIntField(env, clazz, fieldID, env->GetStaticIntField(clazz, fieldID) + change);
}

jlong PostfixAddStaticLongField(JNIEnv *env, jclass clazz, jfieldID fieldID, jlong change) {
    jlong value = env->GetStaticLongField(clazz, fieldID);
    env->SetStaticLongField(clazz, fieldID, value + change);
    return value;
}

jlong PrefixAddStaticLongField(JNIEnv *env, jclass clazz, jfieldID fieldID, jlong change) {
    return SetAndGetStaticLongField(env, clazz, fieldID, env->GetStaticLongField(clazz, fieldID) + change);
}

jfloat PostfixAddStaticFloatField(JNIEnv *env, jclass clazz, jfieldID fieldID, jfloat change) {
    jfloat value = env->GetStaticFloatField(clazz, fieldID);
    env->SetStaticFloatField(clazz, fieldID, value + change);
    return value;
}

jfloat PrefixAddStaticFloatField(JNIEnv *env, jclass clazz, jfieldID fieldID, jfloat change) {
    return SetAndGetStaticFloatField(env, clazz, fieldID, env->GetStaticFloatField(clazz, fieldID) + change);
}

jdouble PostfixAddStaticDoubleField(JNIEnv *env, jclass clazz, jfieldID fieldID, jdouble change) {
    jdouble value = env->GetStaticDoubleField(clazz, fieldID);
    env->SetStaticDoubleField(clazz, fieldID, value + change);
    return value;
}

jdouble PrefixAddStaticDoubleField(JNIEnv *env, jclass clazz, jfieldID fieldID, jdouble change) {
    return SetAndGetStaticDoubleField(env, clazz, fieldID, env->GetStaticDoubleField(clazz, fieldID) + change);
}