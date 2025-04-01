#include <android/log.h>

#include <jni.h>
#include "libslic3r/libslic3r.h"
#include "libslic3r/Config.hpp"
#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PresetBundle.hpp"
#include "libslic3r/ModelArrange.hpp"
#include "libslic3r/SVG.hpp"
#include "libslic3r/Geometry.hpp"
#include "libslic3r/Arrange.hpp"
#include "libslic3r/AABBMesh.hpp"
#include "libslic3r/Geometry/ConvexHull.hpp"
#include "bbl/Orient.hpp"
#include "Viewer.hpp"

#include "GLModel.hpp"
#include "GLShader.hpp"
#include "bed_utils.hpp"
#include "libvgcode_utils.hpp"

#include <igl/unproject.h>
#include <GLES3/gl3.h>

using namespace Slic3r;
using namespace Slic3r::GUI;

#define TAG "SB_Native"

struct PlaneData {
    std::vector<Vec3d> vertices;
    Vec3d normal;
    float area;
};
struct ModelRef {
    Model model;
    std::string base_name;
};
struct GLModelRef {
    GLModel model;
    TriangleMesh mesh;
    AABBMesh* emesh;
    std::vector<stl_normal> normals;
    Vec3d flatten_normal;
};
struct ShaderRef {
    GLShaderProgram program;
};
struct BedRef {
    DynamicPrintConfig config;
    ExPolygon contour;
    GLModel* triangles;
    GLModel* gridlines;
    GLModel* contourlines;
    BuildVolume build_volume;
};
struct GCodeViewerRef {
    libvgcode::Viewer viewer;
    libvgcode::GCodeInputData data;
    bool initialized;
};
struct GCodeResultRef {
    GCodeProcessorResult result;
    std::string name;
};
struct ConfigRef {
    DynamicPrintConfig config;
};

jclass sliceListenerClass;
jmethodID sliceListenerOnProgress;

jclass shadersManagerClass = nullptr;
jmethodID shadersManagerGetCurrent = nullptr;

static JavaVM* staticVM;

GLShaderProgram* get_current_shader() {
    JNIEnv* env;
    if (staticVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return nullptr;
    }

    jlong ptr = env->CallStaticLongMethod(shadersManagerClass, shadersManagerGetCurrent);
    if (ptr == 0) {
        return nullptr;
    }
    ShaderRef* ref = (ShaderRef*) (intptr_t) ptr;
    GLShaderProgram* program = &ref->program;

    return program;
}

extern "C" {
    int JNI_OnLoad(JavaVM *vm, void*) {
        JNIEnv *env;
        if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }

        staticVM = vm;

        sliceListenerClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/SliceListener");
        sliceListenerOnProgress = env->GetMethodID(sliceListenerClass, "onProgress", "(ILjava/lang/String;)V");

        shadersManagerClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/GLShadersManager")));
        shadersManagerGetCurrent = env->GetStaticMethodID(shadersManagerClass, "getCurrentShaderPointer", "()J");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_set_1svg_1path_1prefix(JNIEnv *env, jclass, jstring path) {
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        Slic3r::svg_path_prefix = std::string(chars);
        env->ReleaseStringUTFChars(path, chars);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_get_1print_1config_1def(JNIEnv *env, jclass, jobject def) {
        jclass printConfigDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/PrintConfigDef");
        jmethodID printConfigAddOption = env->GetMethodID(printConfigDefClass, "addOption", "(Ljava/lang/String;Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef;)V");
        jmethodID printConfigResolveEnum = env->GetStaticMethodID(printConfigDefClass, "resolveEnum", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");
        jclass configOptionDefClass = env->FindClass("ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef");
        jmethodID configOptionDefCtr = env->GetMethodID(configOptionDefClass, "<init>", "()V");
        jfieldID keyField = env->GetFieldID(configOptionDefClass, "key", "Ljava/lang/String;");
        jfieldID typeField = env->GetFieldID(configOptionDefClass, "type", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType;");
        jfieldID guiTypeField = env->GetFieldID(configOptionDefClass, "guiType", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType;");
        jfieldID labelField = env->GetFieldID(configOptionDefClass, "label", "Ljava/lang/String;");
        jfieldID fullLabelField = env->GetFieldID(configOptionDefClass, "fullLabel", "Ljava/lang/String;");
        jfieldID printerTechField = env->GetFieldID(configOptionDefClass, "printerTechnology", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology;");
        jfieldID categoryField = env->GetFieldID(configOptionDefClass, "category", "Ljava/lang/String;");
        jfieldID tooltipField = env->GetFieldID(configOptionDefClass, "tooltip", "Ljava/lang/String;");
        jfieldID sidetextField = env->GetFieldID(configOptionDefClass, "sidetext", "Ljava/lang/String;");
        jfieldID multilineField = env->GetFieldID(configOptionDefClass, "multiline", "Z");
        jfieldID fullWidthField = env->GetFieldID(configOptionDefClass, "fullWidth", "Z");
        jfieldID readonlyField = env->GetFieldID(configOptionDefClass, "readonly", "Z");
        jfieldID heightField = env->GetFieldID(configOptionDefClass, "height", "I");
        jfieldID widthField = env->GetFieldID(configOptionDefClass, "width", "I");
        jfieldID minField = env->GetFieldID(configOptionDefClass, "min", "F");
        jfieldID maxField = env->GetFieldID(configOptionDefClass, "max", "F");
        jfieldID modeField = env->GetFieldID(configOptionDefClass, "mode", "Lru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode;");
        jfieldID defaultValueField = env->GetFieldID(configOptionDefClass, "defaultValue", "Ljava/lang/String;");
        jfieldID enumLabelsField = env->GetFieldID(configOptionDefClass, "enumLabels", "[Ljava/lang/String;");
        jfieldID enumValuesField = env->GetFieldID(configOptionDefClass, "enumValues", "[Ljava/lang/String;");

        auto resolveEnum = [&env,&printConfigDefClass,&printConfigResolveEnum](char* className, char* enumValue) {
            jobject key = env->NewStringUTF(className);
            jobject val = env->NewStringUTF(enumValue);

            jobject v = env->CallStaticObjectMethod(printConfigDefClass, printConfigResolveEnum, key, val);

            env->DeleteLocalRef(key);
            env->DeleteLocalRef(val);
            return v;
        };

        PrintConfigDef nDef;
        for (std::string key : nDef.keys()) {
            const ConfigOptionDef* nCfgDef = nDef.get(key);
            ConfigOptionEnumDef* enumDef = nullptr;
            jobject cfgDef = env->NewObject(configOptionDefClass, configOptionDefCtr);
            const char* typeStr;
            switch (nCfgDef->type) {
                default:
                case Slic3r::coNone:
                    typeStr = "NONE";
                    break;
                case Slic3r::coFloat:
                    typeStr = "FLOAT";
                    break;
                case Slic3r::coFloats:
                    typeStr = "FLOATS";
                    break;
                case Slic3r::coInt:
                    typeStr = "INT";
                    break;
                case Slic3r::coInts:
                    typeStr = "INTS";
                    break;
                case Slic3r::coString:
                    typeStr = "STRING";
                    break;
                case Slic3r::coStrings:
                    typeStr = "STRINGS";
                    break;
                case Slic3r::coPercent:
                    typeStr = "PERCENT";
                    break;
                case Slic3r::coPercents:
                    typeStr = "PERCENTS";
                    break;
                case Slic3r::coFloatOrPercent:
                    typeStr = "FLOAT_OR_PERCENT";
                    break;
                case Slic3r::coFloatsOrPercents:
                    typeStr = "FLOATS_OR_PERCENTS";
                    break;
                case Slic3r::coPoint:
                    typeStr = "POINT";
                    break;
                case Slic3r::coPoints:
                    typeStr = "POINTS";
                    break;
                case Slic3r::coPoint3:
                    typeStr = "POINT3";
                    break;
                case Slic3r::coBool:
                    typeStr = "BOOL";
                    break;
                case Slic3r::coBools:
                    typeStr = "BOOLS";
                    break;
                case Slic3r::coEnum:
                    typeStr = "ENUM";
                    enumDef = nCfgDef->enum_def.get();
                    break;
                case Slic3r::coEnums:
                    typeStr = "ENUMS";
                    break;
            }

            const char* guiTypeStr;
            switch (nCfgDef->gui_type) {
                default:
                case Slic3r::ConfigOptionDef::GUIType::undefined:
                    guiTypeStr = "UNDEFINED";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::i_enum_open:
                    guiTypeStr = "I_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::f_enum_open:
                    guiTypeStr = "F_ENUM_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::select_open:
                    guiTypeStr = "SELECT_OPEN";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::color:
                    guiTypeStr = "COLOR";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::slider:
                    guiTypeStr = "SLIDER";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::legend:
                    guiTypeStr = "LEGEND";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::one_string:
                    guiTypeStr = "ONE_STRING";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::select_close:
                    guiTypeStr = "SELECT_CLOSE";
                    break;
                case Slic3r::ConfigOptionDef::GUIType::password:
                    guiTypeStr = "PASSWORD";
                    break;
            }

            const char* techStr;
            switch (nCfgDef->printer_technology) {
                case Slic3r::ptAny:
                    techStr = "ANY";
                    break;
                case Slic3r::ptFFF:
                    techStr = "FFF";
                    break;
                case Slic3r::ptSLA:
                    techStr = "SLA";
                    break;
                default:
                case Slic3r::ptUnknown:
                    techStr = "UNKNOWN";
                    break;
            }

            const char* modeStr;
            switch (nCfgDef->mode) {
                case Slic3r::comSimple:
                    modeStr = "SIMPLE";
                    break;
                case Slic3r::comAdvanced:
                    modeStr = "ADVANCED";
                    break;
                case Slic3r::comExpert:
                    modeStr = "EXPERT";
                    break;
                default:
                case Slic3r::comUndef:
                    modeStr = "UNDEFINED";
                    break;
            }

            jobject keyValue = env->NewStringUTF(key.c_str());
            jobject typeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionType", (char*) typeStr);
            jobject guiTypeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$GUIType", (char*) guiTypeStr);
            jobject labelValue = env->NewStringUTF(nCfgDef->label.c_str());
            jobject fullLabelValue = env->NewStringUTF(nCfgDef->full_label.c_str());
            jobject printerTechValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$PrinterTechnology", (char*) techStr);
            jobject categoryValue = env->NewStringUTF(nCfgDef->category.c_str());
            jobject tooltipValue = env->NewStringUTF(nCfgDef->tooltip.c_str());
            jobject sidetextValue = env->NewStringUTF(nCfgDef->sidetext.c_str());
            jobject modeValue = resolveEnum((char*) "ru/ytkab0bp/slicebeam/slic3r/ConfigOptionDef$ConfigOptionMode", (char*) modeStr);

            env->SetObjectField(cfgDef, keyField, keyValue);
            env->SetObjectField(cfgDef, typeField, typeValue);
            env->SetObjectField(cfgDef, guiTypeField, guiTypeValue);
            env->SetObjectField(cfgDef, labelField, labelValue);
            env->SetObjectField(cfgDef, fullLabelField, fullLabelValue);
            env->SetObjectField(cfgDef, printerTechField, printerTechValue);
            env->SetObjectField(cfgDef, categoryField, categoryValue);
            env->SetObjectField(cfgDef, tooltipField, tooltipValue);
            env->SetObjectField(cfgDef, sidetextField, sidetextValue);
            env->SetBooleanField(cfgDef, multilineField, nCfgDef->multiline);
            env->SetBooleanField(cfgDef, fullWidthField, nCfgDef->full_width);
            env->SetBooleanField(cfgDef, readonlyField, nCfgDef->readonly);
            env->SetIntField(cfgDef, heightField, nCfgDef->height);
            env->SetIntField(cfgDef, widthField, nCfgDef->width);
            env->SetFloatField(cfgDef, minField, nCfgDef->min);
            env->SetFloatField(cfgDef, maxField, nCfgDef->max);
            env->SetObjectField(cfgDef, modeField, modeValue);
            if (enumDef != nullptr) {
                const std::vector<std::string> labels = enumDef->labels();
                jobjectArray labelsArr = env->NewObjectArray(labels.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < labels.size(); i++) {
                    jobject str = env->NewStringUTF(labels[i].c_str());
                    env->SetObjectArrayElement(labelsArr, i, str);
                    env->DeleteLocalRef(str);
                }

                std::vector<std::string> values = enumDef->values();
                jobjectArray valuesArr = env->NewObjectArray(values.size(), env->FindClass("java/lang/String"), nullptr);
                for (int i = 0; i < values.size(); i++) {
                    jobject str = env->NewStringUTF(values[i].c_str());
                    env->SetObjectArrayElement(valuesArr, i, str);
                    env->DeleteLocalRef(str);
                }

                env->SetObjectField(cfgDef, enumLabelsField, labelsArr);
                env->SetObjectField(cfgDef, enumValuesField, valuesArr);

                env->DeleteLocalRef(labelsArr);
                env->DeleteLocalRef(valuesArr);
            }

            const ConfigOption* defValue = nCfgDef->get_default_value<ConfigOption>();
            if (defValue != nullptr) {
                jobject defValueObj = env->NewStringUTF(defValue->serialize().c_str());
                env->SetObjectField(cfgDef, defaultValueField, defValueObj);
                env->DeleteLocalRef(defValueObj);
            }

            env->CallVoidMethod(def, printConfigAddOption, keyValue, cfgDef);

            env->DeleteLocalRef(cfgDef);
            env->DeleteLocalRef(keyValue);
            env->DeleteLocalRef(typeValue);
            env->DeleteLocalRef(guiTypeValue);
            env->DeleteLocalRef(labelValue);
            env->DeleteLocalRef(fullLabelValue);
            env->DeleteLocalRef(printerTechValue);
            env->DeleteLocalRef(categoryValue);
            env->DeleteLocalRef(tooltipValue);
            env->DeleteLocalRef(sidetextValue);
            env->DeleteLocalRef(modeValue);
        }
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1read_1from_1file(JNIEnv *env, jclass, jstring path, jstring base_name) {
        const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
        const char* baseChars = env->GetStringUTFChars(base_name, JNI_FALSE);

        ModelRef* ref;
        try {
            ref = new ModelRef();
            ref->model = Model::read_from_file(std::string(chars), nullptr, nullptr, Model::LoadAttribute::AddDefaultInstances);
            ref->base_name = std::string(baseChars);
        } catch (const Slic3r::RuntimeError& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
            return 0;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return 0;
        }

        env->ReleaseStringUTFChars(path, chars);
        env->ReleaseStringUTFChars(base_name, baseChars);

        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create(JNIEnv *env, jclass) {
        ModelRef* ref = new ModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_models_1merge(JNIEnv* env, jclass, jlongArray ptrsArr) {
        ModelRef* ref = new ModelRef();

        jlong* ptrs = env->GetLongArrayElements(ptrsArr, JNI_FALSE);
        int len = env->GetArrayLength(ptrsArr);
        for (int i = 0; i < len; i++) {
            ModelRef* sRef = (ModelRef*) (intptr_t) ptrs[i];
            for (ModelObject* obj : sRef->model.objects) {
                ref->model.add_object(*obj);
            }
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1objects_1count(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        return model->model.objects.size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1add_1object_1from_1another(JNIEnv* env, jclass, jlong ptr, jlong fromPtr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelRef* from = (ModelRef *) (intptr_t) fromPtr;
        model->model.add_object(*from->model.objects[i]);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1delete_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.delete_object(i);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1rotation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_rotation().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1mirror(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_mirror().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1scale(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, obj->volumes[0]->get_scaling_factor().data());
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1translation(JNIEnv* env, jclass, jlong ptr, jint object_index) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[object_index];
        Vec3d offset = obj->bounding_box_exact().center();
        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, offset.data());
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.objects[i]->translate(x, y, z);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1ensure_1on_1bed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.objects[i]->ensure_on_bed(false);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1scale(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        Vec3d factor(x, y, z);
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            ptrs[i]->set_scaling_factor(factor);
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1left_1handed(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        return model->model.objects[i]->volumes[0]->is_left_handed();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        Vec3d vec(x, y, z);
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            Vec3d current_rotation = ptrs[i]->get_rotation();

            Eigen::Quaterniond q_current =
                    Eigen::AngleAxisd(current_rotation[2], Eigen::Vector3d::UnitZ()) *
                    Eigen::AngleAxisd(current_rotation[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(current_rotation[0], Eigen::Vector3d::UnitX());

            Eigen::Quaterniond q_delta =
                    Eigen::AngleAxisd(vec[0], Eigen::Vector3d::UnitX()) *
                    Eigen::AngleAxisd(vec[1], Eigen::Vector3d::UnitY()) *
                    Eigen::AngleAxisd(vec[2], Eigen::Vector3d::UnitZ());

            Eigen::Quaterniond q_result = q_delta * q_current;
            Eigen::Vector3d new_rotation = q_result.toRotationMatrix().eulerAngles(2, 1, 0);
            ptrs[i]->set_rotation(Vec3d(new_rotation[2], new_rotation[1], new_rotation[0]));
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1flatten_1rotate(JNIEnv* env, jclass, jlong ptr, jint i, jlong surface_ptr) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        GLModelRef* surface = (GLModelRef*) (intptr_t) surface_ptr;

        const Vec3d& normal = surface->flatten_normal;
        ModelVolumePtrs ptrs = model->model.objects[i]->volumes;
        for (int i = 0, c = ptrs.size(); i < c; i++) {
            auto vol = ptrs[i];
            const Geometry::Transformation& old_transform = vol->get_transformation();
            const Vec3d tnormal = normal;
            const Transform3d rotation_matrix = Transform3d(Eigen::Quaterniond().setFromTwoVectors(tnormal, -Vec3d::UnitZ()));
            vol->set_transformation(old_transform.get_offset_matrix() * rotation_matrix * old_transform.get_matrix_no_offset());
        }
        model->model.objects[i]->invalidate_bounding_box();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate_1global(JNIEnv* env, jclass, jlong ptr, jdouble x, jdouble y, jdouble z) {
        ModelRef* model = (ModelRef *) (intptr_t) ptr;
        model->model.translate(x, y, z);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_approx().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_approx().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_approx().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_approx().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_approx().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.objects[i]->bounding_box_exact().min.x();
        elements[1] = ref->model.objects[i]->bounding_box_exact().min.y();
        elements[2] = ref->model.objects[i]->bounding_box_exact().min.z();
        elements[3] = ref->model.objects[i]->bounding_box_exact().max.x();
        elements[4] = ref->model.objects[i]->bounding_box_exact().max.y();
        elements[5] = ref->model.objects[i]->bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1approx_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_approx().min.x();
        elements[1] = ref->model.bounding_box_approx().min.y();
        elements[2] = ref->model.bounding_box_approx().min.z();
        elements[3] = ref->model.bounding_box_approx().max.x();
        elements[4] = ref->model.bounding_box_approx().max.y();
        elements[5] = ref->model.bounding_box_approx().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact_1global(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->model.bounding_box_exact().min.x();
        elements[1] = ref->model.bounding_box_exact().min.y();
        elements[2] = ref->model.bounding_box_exact().min.z();
        elements[3] = ref->model.bounding_box_exact().max.x();
        elements[4] = ref->model.bounding_box_exact().max.y();
        elements[5] = ref->model.bounding_box_exact().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1create_1flatten_1planes(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* ref = (ModelRef*) (intptr_t) ptr;
        const ModelObject* mo = ref->model.objects[i];
        TriangleMesh ch;
        Transform3d real_transform = Geometry::translation_transform(mo->bounding_box_exact().center());
        for (const ModelVolume* vol : mo->volumes) {
            if (vol->type() != ModelVolumeType::MODEL_PART)
                continue;
            TriangleMesh vol_ch = vol->get_convex_hull();
            vol_ch.transform(vol->get_matrix_no_offset());
            vol_ch.transform(real_transform);
            ch.merge(vol_ch);
        }
        ch = ch.convex_hull_3d();

        std::vector<PlaneData> m_planes;

        const Transform3d inst_matrix = mo->instances.front()->get_matrix_no_offset();

        // Following constants are used for discarding too small polygons.
        const float minimal_area = 5.f; // in square mm (world coordinates)
        const float minimal_side = 1.f; // mm

        const int                num_of_facets  = ch.facets_count();
        const std::vector<Vec3f> face_normals   = its_face_normals(ch.its);
        const std::vector<Vec3i> face_neighbors = its_face_neighbors(ch.its);
        std::vector<int>         facet_queue(num_of_facets, 0);
        std::vector<bool>        facet_visited(num_of_facets, false);
        int                      facet_queue_cnt = 0;
        const stl_normal*        normal_ptr      = nullptr;
        int facet_idx = 0;
        while (true) {
            // Find next unvisited triangle:
            for (; facet_idx < num_of_facets; ++ facet_idx)
                if (!facet_visited[facet_idx]) {
                    facet_queue[facet_queue_cnt ++] = facet_idx;
                    facet_visited[facet_idx] = true;
                    normal_ptr = &face_normals[facet_idx];
                    m_planes.emplace_back();
                    break;
                }
            if (facet_idx == num_of_facets)
                break; // Everything was visited already

            while (facet_queue_cnt > 0) {
                int facet_idx = facet_queue[-- facet_queue_cnt];
                const stl_normal& this_normal = face_normals[facet_idx];
                if (std::abs(this_normal(0) - (*normal_ptr)(0)) < 0.001 && std::abs(this_normal(1) - (*normal_ptr)(1)) < 0.001 && std::abs(this_normal(2) - (*normal_ptr)(2)) < 0.001) {
                    const Vec3i face = ch.its.indices[facet_idx];
                    for (int j=0; j<3; ++j)
                        m_planes.back().vertices.emplace_back(ch.its.vertices[face[j]].cast<double>());

                    facet_visited[facet_idx] = true;
                    for (int j = 0; j < 3; ++ j)
                        if (int neighbor_idx = face_neighbors[facet_idx][j]; neighbor_idx >= 0 && ! facet_visited[neighbor_idx])
                            facet_queue[facet_queue_cnt ++] = neighbor_idx;
                }
            }
            m_planes.back().normal = normal_ptr->cast<double>();

            Pointf3s& verts = m_planes.back().vertices;
            // Now we'll transform all the points into world coordinates, so that the areas, angles and distances
            // make real sense.
            verts = transform(verts, inst_matrix);

            // if this is a just a very small triangle, remove it to speed up further calculations (it would be rejected later anyway):
            if (verts.size() == 3 &&
                ((verts[0] - verts[1]).norm() < minimal_side
                 || (verts[0] - verts[2]).norm() < minimal_side
                 || (verts[1] - verts[2]).norm() < minimal_side))
                m_planes.pop_back();
        }

        // Let's prepare transformation of the normal vector from mesh to instance coordinates.
        const Matrix3d normal_matrix = inst_matrix.matrix().block(0, 0, 3, 3).inverse().transpose();

        // Now we'll go through all the polygons, transform the points into xy plane to process them:
        for (unsigned int polygon_id=0; polygon_id < m_planes.size(); ++polygon_id) {
            Pointf3s& polygon = m_planes[polygon_id].vertices;
            const Vec3d& normal = m_planes[polygon_id].normal;

            // transform the normal according to the instance matrix:
            const Vec3d normal_transformed = normal_matrix * normal;

            // We are going to rotate about z and y to flatten the plane
            Eigen::Quaterniond q;
            Transform3d m = Transform3d::Identity();
            m.matrix().block(0, 0, 3, 3) = q.setFromTwoVectors(normal_transformed, Vec3d::UnitZ()).toRotationMatrix();
            polygon = transform(polygon, m);

            // Now to remove the inner points. We'll misuse Geometry::convex_hull for that, but since
            // it works in fixed point representation, we will rescale the polygon to avoid overflows.
            // And yes, it is a nasty thing to do. Whoever has time is free to refactor.
            Vec3d bb_size = BoundingBoxf3(polygon).size();
            float sf = std::min(1./bb_size(0), 1./bb_size(1));
            Transform3d tr = Geometry::scale_transform({ sf, sf, 1.f });
            polygon = transform(polygon, tr);
            polygon = Slic3r::Geometry::convex_hull(polygon);
            polygon = transform(polygon, tr.inverse());

            // Calculate area of the polygons and discard ones that are too small
            float& area = m_planes[polygon_id].area;
            area = 0.f;
            for (unsigned int i = 0; i < polygon.size(); i++) // Shoelace formula
                area += polygon[i](0)*polygon[i + 1 < polygon.size() ? i + 1 : 0](1) - polygon[i + 1 < polygon.size() ? i + 1 : 0](0)*polygon[i](1);
            area = 0.5f * std::abs(area);

            bool discard = false;
            if (area < minimal_area)
                discard = true;
            else {
                // We also check the inner angles and discard polygons with angles smaller than the following threshold
                const double angle_threshold = ::cos(10.0 * (double)PI / 180.0);

                for (unsigned int i = 0; i < polygon.size(); ++i) {
                    const Vec3d& prec = polygon[(i == 0) ? polygon.size() - 1 : i - 1];
                    const Vec3d& curr = polygon[i];
                    const Vec3d& next = polygon[(i == polygon.size() - 1) ? 0 : i + 1];

                    if ((prec - curr).normalized().dot((next - curr).normalized()) > angle_threshold) {
                        discard = true;
                        break;
                    }
                }
            }

            if (discard) {
                m_planes[polygon_id--] = std::move(m_planes.back());
                m_planes.pop_back();
                continue;
            }

            // We will shrink the polygon a little bit so it does not touch the object edges:
            Vec3d centroid = std::accumulate(polygon.begin(), polygon.end(), Vec3d(0.0, 0.0, 0.0));
            centroid /= (double)polygon.size();
            for (auto& vertex : polygon)
                vertex = 0.9f*vertex + 0.1f*centroid;

            // Polygon is now simple and convex, we'll round the corners to make them look nicer.
            // The algorithm takes a vertex, calculates middles of respective sides and moves the vertex
            // towards their average (controlled by 'aggressivity'). This is repeated k times.
            // In next iterations, the neighbours are not always taken at the middle (to increase the
            // rounding effect at the corners, where we need it most).
            const unsigned int k = 10; // number of iterations
            const float aggressivity = 0.2f;  // agressivity
            const unsigned int N = polygon.size();
            std::vector<std::pair<unsigned int, unsigned int>> neighbours;
            if (k != 0) {
                Pointf3s points_out(2*k*N); // vector long enough to store the future vertices
                for (unsigned int j=0; j<N; ++j) {
                    points_out[j*2*k] = polygon[j];
                    neighbours.push_back(std::make_pair((int)(j*2*k-k) < 0 ? (N-1)*2*k+k : j*2*k-k, j*2*k+k));
                }

                for (unsigned int i=0; i<k; ++i) {
                    // Calculate middle of each edge so that neighbours points to something useful:
                    for (unsigned int j=0; j<N; ++j)
                        if (i==0)
                            points_out[j*2*k+k] = 0.5f * (points_out[j*2*k] + points_out[j==N-1 ? 0 : (j+1)*2*k]);
                        else {
                            float r = 0.2+0.3/(k-1)*i; // the neighbours are not always taken in the middle
                            points_out[neighbours[j].first] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].first-1];
                            points_out[neighbours[j].second] = r*points_out[j*2*k] + (1-r) * points_out[neighbours[j].second+1];
                        }
                    // Now we have a triangle and valid neighbours, we can do an iteration:
                    for (unsigned int j=0; j<N; ++j)
                        points_out[2*k*j] = (1-aggressivity) * points_out[2*k*j] +
                                            aggressivity*0.5f*(points_out[neighbours[j].first] + points_out[neighbours[j].second]);

                    for (auto& n : neighbours) {
                        ++n.first;
                        --n.second;
                    }
                }
                polygon = points_out; // replace the coarse polygon with the smooth one that we just created
            }


            // Raise a bit above the object surface to avoid flickering:
            for (auto& b : polygon)
                b(2) += 0.1f;

            // Transform back to 3D (and also back to mesh coordinates)
            polygon = transform(polygon, inst_matrix.inverse() * m.inverse());
        }

        // We'll sort the planes by area and only keep the 254 largest ones (because of the picking pass limitations):
        std::sort(m_planes.rbegin(), m_planes.rend(), [](const PlaneData& a, const PlaneData& b) { return a.area < b.area; });
        m_planes.resize(std::min((int)m_planes.size(), 254));

        jlongArray arr = env->NewLongArray(m_planes.size());

        // And finally create respective VBOs. The polygon is convex with
        // the vertices in order, so triangulation is trivial.
        for (int i = 0, s = m_planes.size(); i < s; i++) {
            auto& plane = m_planes[i];
            indexed_triangle_set its;
            its.vertices.reserve(plane.vertices.size());
            its.indices.reserve(plane.vertices.size() / 3);
            for (size_t i = 0; i < plane.vertices.size(); ++i) {
                its.vertices.emplace_back((Vec3f)plane.vertices[i].cast<float>());
            }
            for (size_t i = 1; i < plane.vertices.size() - 1; ++i) {
                its.indices.emplace_back(0, i, i + 1); // triangle fan
            }

            if (Geometry::Transformation(inst_matrix).is_left_handed()) {
                // we need to swap face normals in case the object is mirrored
                // for the raycaster to work properly
                for (stl_triangle_vertex_indices& face : its.indices) {
                    if (its_face_normal(its, face).cast<double>().dot(plane.normal) < 0.0)
                        std::swap(face[1], face[2]);
                }
            }
            GLModelRef* ref = new GLModelRef();
            ref->mesh = TriangleMesh(its);
            ref->model.init_from(its);
            ref->flatten_normal = plane.normal;
            ref->emesh = new AABBMesh(its, true);
            ref->normals = its_face_normals(its);

            jlong ptr = reinterpret_cast<jlong>(ref);
            env->SetLongArrayRegion(arr, i, 1, &ptr);

            // vertices are no more needed, clear memory
            plane.vertices = std::vector<Vec3d>();
        }
        m_planes.clear();
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1auto_1orient(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        orientation::orient(obj);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1is_1big_1object(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->volumes.size() == 1 && obj->volumes.front()->mesh().its.indices.size() >= 100000;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1extruder(JNIEnv* env, jclass, jlong ptr, jint i) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        return obj->config.has("extruder") ? obj->config.opt_int("extruder") : -1;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1set_1extruder(JNIEnv* env, jclass, jlong ptr, jint i, jint extruder) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        ModelObject* obj = model->model.objects[i];
        obj->config.set("extruder", extruder);
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1slice(JNIEnv* env, jclass, jlong ptr, jstring configPath, jstring path, jobject listener) {
        try {
            ModelRef* model = (ModelRef*) (intptr_t) ptr;

            Print print;
            PresetBundle bundle;
            DynamicPrintConfig config;
            const char *chars = env->GetStringUTFChars(configPath, JNI_FALSE);
            config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
            env->ReleaseStringUTFChars(path, chars);
            config.normalize_fdm();

            for (auto* mo : model->model.objects) {
                print.auto_assign_extruders(mo);
            }

            std::string err = config.validate();
            if (!err.empty()) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }
            print.apply(model->model, config);

            err = print.validate();
            if (!err.empty()) {
                env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.c_str());
                return 0;
            }

            std::thread::id id = std::this_thread::get_id();

            print.set_status_callback([&id, &listener](const Slic3r::PrintBase::SlicingStatus &s) {
                bool needAttach = id != std::this_thread::get_id();

                JNIEnv* e;
                if (staticVM->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_1_6) != JNI_OK) {
                    return;
                }
                if (needAttach) {
                    JavaVMAttachArgs args;
                    args.name = nullptr;
                    args.group = nullptr;
                    args.version = JNI_VERSION_1_6;
                    staticVM->AttachCurrentThread(&e, &args);
                }
                e->CallVoidMethod(listener, sliceListenerOnProgress, s.percent, e->NewStringUTF(s.text.c_str()));
                if (needAttach) {
                    staticVM->DetachCurrentThread();
                }
            });
            print.process();

            chars = env->GetStringUTFChars(path, JNI_FALSE);
            GCodeResultRef* resultRef = new GCodeResultRef();
            print.export_gcode(std::string(chars), &resultRef->result, nullptr);
            env->ReleaseStringUTFChars(path, chars);

            resultRef->name = print.output_filename(model->base_name);

            return (jlong) (intptr_t) resultRef;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), e.what());
            return 0;
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1release(JNIEnv* env, jclass, jlong ptr) {
        ModelRef* model = (ModelRef*) (intptr_t) ptr;
        delete model;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1load_1file(JNIEnv* env, jclass, jstring path, jstring name) {
        GCodeResultRef* ref = new GCodeResultRef();

        GCodeProcessor processor;
        try {
            const char* chars = env->GetStringUTFChars(path, JNI_FALSE);
            const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
            ref->name = std::string(nameChars);

            processor.process_file(chars, [](float value) {
                // TODO: Notify progress value
            });
            ref->result = std::move(processor.extract_result());

            env->ReleaseStringUTFChars(path, chars);
            env->ReleaseStringUTFChars(name, nameChars);

            return (jlong) (intptr_t) ref;
        } catch (const std::exception& e) {
            env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
            return 0;
        }
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1recommended_1name(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        return env->NewStringUTF(ref->name.c_str());
    }

    GCodeExtrusionRole mapGCodeRole(int index) {
        GCodeExtrusionRole gRole;
        switch (index) {
            default:
            case 0:
                gRole = GCodeExtrusionRole::None;
                break;
            case 1:
                gRole = GCodeExtrusionRole::Perimeter;
                break;
            case 2:
                gRole = GCodeExtrusionRole::ExternalPerimeter;
                break;
            case 3:
                gRole = GCodeExtrusionRole::OverhangPerimeter;
                break;
            case 4:
                gRole = GCodeExtrusionRole::InternalInfill;
                break;
            case 5:
                gRole = GCodeExtrusionRole::SolidInfill;
                break;
            case 6:
                gRole = GCodeExtrusionRole::TopSolidInfill;
                break;
            case 7:
                gRole = GCodeExtrusionRole::Ironing;
                break;
            case 8:
                gRole = GCodeExtrusionRole::BridgeInfill;
                break;
            case 9:
                gRole = GCodeExtrusionRole::GapFill;
                break;
            case 10:
                gRole = GCodeExtrusionRole::Skirt;
                break;
            case 11:
                gRole = GCodeExtrusionRole::SupportMaterial;
                break;
            case 12:
                gRole = GCodeExtrusionRole::SupportMaterialInterface;
                break;
            case 13:
                gRole = GCodeExtrusionRole::WipeTower;
                break;
            case 14:
                gRole = GCodeExtrusionRole::Custom;
                break;
        }
        return gRole;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1mm(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        std::pair<double, double> info = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role))->second;
        return info.first * 1000.0 / 25.4;
    }

    JNIEXPORT jdouble JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1g(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;

        std::pair<double, double> info = ref->result.print_statistics.used_filaments_per_role.find(mapGCodeRole(role))->second;
        return info.second;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeResultRef* ref = (GCodeResultRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1init_1from_1texts(JNIEnv* env, jclass, jstring name, jstring fsText, jstring vsText) {
        const char* nameChars = env->GetStringUTFChars(name, JNI_FALSE);
        const char* fsChars = env->GetStringUTFChars(fsText, JNI_FALSE);
        const char* vsChars = env->GetStringUTFChars(vsText, JNI_FALSE);
        GLShaderProgram::ShaderSources sources = {};
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Vertex)] = std::string(vsChars);
        sources[static_cast<size_t>(GLShaderProgram::EShaderType::Fragment)] = std::string(fsChars);
        ShaderRef* ref = new ShaderRef();
        ref->program.init_from_texts(std::string(nameChars), sources);

        env->ReleaseStringUTFChars(name, nameChars);
        env->ReleaseStringUTFChars(fsText, fsChars);
        env->ReleaseStringUTFChars(vsText, vsChars);

        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1create(JNIEnv* env, jclass) {
        GLModelRef* ref = new GLModelRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1raycast_1data(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->emesh = new AABBMesh(ref->mesh, true);
        ref->normals = its_face_normals(ref->mesh.its);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1raycast_1closest_1hit(JNIEnv* env, jclass, jlong ptr, jdoubleArray pointArr, jdoubleArray directionArr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        jdouble* point = env->GetDoubleArrayElements(pointArr, JNI_FALSE);
        jdouble* direction = env->GetDoubleArrayElements(directionArr, JNI_FALSE);

        Vec3d point3d(point);
        Vec3d direction3d(direction);

        Vec3d point_positive = point3d - direction3d;
        Vec3d point_negative = point3d + direction3d;

        std::vector<AABBMesh::hit_result> hits = ref->emesh->query_ray_hits(point_positive, direction3d);
        jdoubleArray arr = env->NewDoubleArray(hits.size() * 6);
        for (int i = 0; i < hits.size(); ++i) {
            const AABBMesh::hit_result& hit = hits[i];
            env->SetDoubleArrayRegion(arr, i * 6, 6, hit.position().data());
        }

        env->ReleaseDoubleArrayElements(pointArr, point, JNI_ABORT);
        env->ReleaseDoubleArrayElements(directionArr, direction, JNI_ABORT);

        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model(JNIEnv* env, jclass, jlong ptr, jlong model) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        ref->mesh = mRef->model.mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1from_1model_1object(JNIEnv* env, jclass, jlong ptr, jlong model, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;
        ref->mesh = mRef->model.objects[i]->mesh();
        ref->model.init_from(ref->mesh.its);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1set_1color(JNIEnv* env, jclass, jlong ptr, jfloat red, jfloat green, jfloat blue, jfloat alpha) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.set_color(ColorRGBA(red, green, blue, alpha));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1stilized_1arrow(JNIEnv* env, jclass, jlong ptr, jfloat tip_radius, jfloat tip_length, jfloat stem_radius, jfloat stem_length) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.init_from(stilized_arrow(16, tip_radius, tip_length, stem_radius, stem_length));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1background_1triangles(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();

        GLModel::Geometry init_data;
        init_data.format = { GLModel::Geometry::EPrimitiveType::Triangles, GLModel::Geometry::EVertexLayout::P2T2 };
        init_data.reserve_vertices(4);
        init_data.reserve_indices(6);

        // vertices
        init_data.add_vertex(Vec2f(-1.0f, -1.0f), Vec2f(0.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, -1.0f),  Vec2f(1.0f, 0.0f));
        init_data.add_vertex(Vec2f(1.0f, 1.0f),   Vec2f(1.0f, 1.0f));
        init_data.add_vertex(Vec2f(-1.0f, 1.0f),  Vec2f(0.0f, 1.0f));

        // indices
        init_data.add_triangle(0, 1, 2);
        init_data.add_triangle(2, 3, 0);

        ref->model.init_from(std::move(init_data));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1init_1bounding_1box(JNIEnv* env, jclass, jlong ptr, jlong modelPtr, jint i) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ModelRef* modelRef = (ModelRef*) (intptr_t) modelPtr;

        const BoundingBoxf3& box = modelRef->model.objects[i]->bounding_box_approx();
        const BoundingBoxf3& curr_box = ref->model.get_bounding_box();
        if (!ref->model.is_initialized() || !is_approx(box.min, curr_box.min) || !is_approx(box.max, curr_box.max)) {
            ref->model.reset();

            const Vec3f b_min = box.min.cast<float>();
            const Vec3f b_max = box.max.cast<float>();
            const Vec3f size = 0.2f * box.size().cast<float>();

            GLModel::Geometry init_data;
            init_data.format = { GLModel::Geometry::EPrimitiveType::Lines, GLModel::Geometry::EVertexLayout::P3 };
            init_data.reserve_vertices(48);
            init_data.reserve_indices(48);

            // vertices
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_min.z() + size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y() + size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_min.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x() - size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_max.x(), b_max.y(), b_max.z() - size.z()));

            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x() + size.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y() - size.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z()));
            init_data.add_vertex(Vec3f(b_min.x(), b_max.y(), b_max.z() - size.z()));

            // indices
            for (unsigned int i = 0; i < 48; ++i) {
                init_data.add_index(i);
            }

            ref->model.init_from(std::move(init_data));
        }
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1render(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.render();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1reset(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        ref->mesh.clear();
        ref->emesh = nullptr;
        ref->normals.clear();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_initialized();
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1is_1empty(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        return ref->model.is_empty();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_glmodel_1release(JNIEnv* env, jclass, jlong ptr) {
        GLModelRef* ref = (GLModelRef*) (intptr_t) ptr;
        ref->model.reset();
        delete ref;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1id(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        return shader->program.get_id();
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1uniform_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char* chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        int location = shader->program.get_uniform_location(chars);
        env->ReleaseStringUTFChars(name, chars);
        return location;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1get_1attrib_1location(JNIEnv* env, jclass, jlong ptr, jstring name) {
        const char* chars = env->GetStringUTFChars(name, JNI_FALSE);
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        int location = shader->program.get_attrib_location(chars);
        env->ReleaseStringUTFChars(name, chars);
        return location;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1start_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.start_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1stop_1using(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        shader->program.stop_using();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_shader_1release(JNIEnv* env, jclass, jlong ptr) {
        ShaderRef* shader = (ShaderRef*) (intptr_t) ptr;
        delete shader;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1calc_1view_1normal_1matrix(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray world_matrix, jdoubleArray normal_matrix) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* worldMatrix = env->GetDoubleArrayElements(world_matrix, JNI_FALSE);

        Matrix4d mViewMatrix(viewMatrix);
        Matrix4d mWorldMatrix(worldMatrix);

        Matrix3d mNormalMatrix = mViewMatrix.block(0, 0, 3, 3) * mWorldMatrix.block(0, 0, 3, 3).inverse().transpose();
        env->SetDoubleArrayRegion(normal_matrix, 0, 12, mNormalMatrix.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(world_matrix, worldMatrix, JNI_ABORT);
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1create(JNIEnv* env, jclass, jstring config) {
        ConfigRef* ref = new ConfigRef();
        const char* config_ini = env->GetStringUTFChars(config, JNI_FALSE);
        ref->config.load_from_ini_string(config_ini, ForwardCompatibilitySubstitutionRule::Disable);

        const ConfigOption *opt = ref->config.option("nozzle_diameter");
        if (opt)
            ref->config.set_key_value("num_extruders", new ConfigOptionInt((int)static_cast<const ConfigOptionFloats*>(opt)->values.size()));

        env->ReleaseStringUTFChars(config, config_ini);
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1release(JNIEnv* env, jclass, jlong ptr) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1check_1compatibility(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef* ref = (ConfigRef*) (intptr_t) ptr;
        const char* condition = env->GetStringUTFChars(cond, JNI_FALSE);

        jboolean value;
        try {
            value = PlaceholderParser::evaluate_boolean_expression(condition, ref->config);
        } catch (const std::runtime_error &err) {
            //FIXME in case of an error, return "compatible with everything".
            __android_log_print(ANDROID_LOG_WARN, TAG, "Parsing error of compatible_printers_condition:\n%s\n", err.what());
            value = true;
        }
        env->ReleaseStringUTFChars(cond, condition);
        return value;
    }

    JNIEXPORT jstring JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1config_1eval(JNIEnv* env, jclass, jlong ptr, jstring cond) {
        ConfigRef *ref = (ConfigRef *) (intptr_t) ptr;
        const char *condition = env->GetStringUTFChars(cond, JNI_FALSE);

        try {
            PlaceholderParser parser(&ref->config);
            std::string val = parser.process(std::string(condition));
            env->ReleaseStringUTFChars(cond, condition);
            return env->NewStringUTF(val.c_str());
        } catch (const std::runtime_error &err) {
            env->ReleaseStringUTFChars(cond, condition);

            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), err.what());
            return nullptr;
        }
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_utils_1unproject(JNIEnv* env, jclass, jdoubleArray view_matrix, jdoubleArray projection_matrix, jint screen_width, jint screen_height, jdouble screen_x, jdouble screen_y) {
        jdouble* viewMatrix = env->GetDoubleArrayElements(view_matrix, JNI_FALSE);
        jdouble* projectionMatrix = env->GetDoubleArrayElements(projection_matrix, JNI_FALSE);

        Matrix4d modelview(viewMatrix);
        Matrix4d projection(projectionMatrix);
        Vec4i viewport(0, 0, screen_width, screen_height);
        Vec3d screenPoint(screen_x, screen_height - screen_y, 0);

        Vec3d point;
        igl::unproject(screenPoint, modelview, projection, viewport, point);

        jdoubleArray arr = env->NewDoubleArray(3);
        env->SetDoubleArrayRegion(arr, 0, 3, point.data());

        env->ReleaseDoubleArrayElements(view_matrix, viewMatrix, JNI_ABORT);
        env->ReleaseDoubleArrayElements(projection_matrix, projectionMatrix, JNI_ABORT);

        return arr;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1create(JNIEnv* env, jclass, jlongArray data) {
        BedRef* ref = new BedRef();
        GLModelRef* refs = new GLModelRef[3];
        refs[0] = GLModelRef();
        ref->triangles = &refs[0].model;
        refs[1] = GLModelRef();
        ref->gridlines = &refs[1].model;
        refs[2] = GLModelRef();
        ref->contourlines = &refs[2].model;

        for (int i = 0; i < 3; i++) {
            jlong ptrLong = (jlong) (intptr_t) &refs[i];
            env->SetLongArrayRegion(data, i, 1, &ptrLong);
        }
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1configure(JNIEnv* env, jclass, jlong ptr, jstring config_path) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;

        const char* chars = env->GetStringUTFChars(config_path, JNI_FALSE);
        ref->config.load(std::string(chars), ForwardCompatibilitySubstitutionRule::Disable);
        env->ReleaseStringUTFChars(config_path, chars);

        const Pointfs bed_shape = ref->config.option_throw<ConfigOptionPoints>("bed_shape")->values;
        float maxHeight = ref->config.option_throw<ConfigOptionFloat>("max_print_height")->value;

        ref->contour = ExPolygon(Polygon::new_scale(bed_shape));
        const BoundingBox bbox = ref->contour.contour.bounding_box();
        if (!bbox.defined) {
            env->ThrowNew(env->FindClass("ru/ytkab0bp/slicebeam/slic3r/Slic3rRuntimeError"), "Invalid bed shape");
            return;
        }

        ref->build_volume = BuildVolume { bed_shape, maxHeight };
        ref->triangles->reset();
        ref->gridlines->reset();
        ref->contourlines->reset();

        bed_util_init_gridlines(ref->contour, ref->gridlines);
        bed_util_init_triangles(ref->contour, ref->triangles);
        bed_util_init_contourlines(ref->contour, ref->contourlines);
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1arrange(JNIEnv* env, jclass, jlong ptr, jlong model) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        ModelRef* mRef = (ModelRef*) (intptr_t) model;

        DynamicPrintConfig config = ref->config;
        arr2::ArrangeBed bed = arr2::to_arrange_bed(get_bed_shape(config));
        arr2::ArrangeSettings arrange_cfg;
        return arrange_objects(mRef->model, bed, arrange_cfg);
    }

    JNIEXPORT jdoubleArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        jdoubleArray arr = env->NewDoubleArray(6);
        jdouble* elements = new jdouble[6];
        elements[0] = ref->build_volume.bounding_volume().min.x();
        elements[1] = ref->build_volume.bounding_volume().min.y();
        elements[2] = ref->build_volume.bounding_volume().min.z();
        elements[3] = ref->build_volume.bounding_volume().max.x();
        elements[4] = ref->build_volume.bounding_volume().max.y();
        elements[5] = ref->build_volume.bounding_volume().max.z();
        env->SetDoubleArrayRegion(arr, 0, 6, elements);
        return arr;
    }

    JNIEXPORT jint JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1get_1bounding_1volume_1max_1size(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        return ref->build_volume.bounding_volume().max_size();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_bed_1release(JNIEnv* env, jclass, jlong ptr) {
        BedRef* ref = (BedRef*) (intptr_t) ptr;
        delete ref;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1create(JNIEnv* env, jclass) {
        GCodeViewerRef* ref = new GCodeViewerRef();
        return (jlong) (intptr_t) ref;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1colors(JNIEnv* env, jclass, jlong ptr, jintArray colorsArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;

        jint* colors = env->GetIntArrayElements(colorsArr, JNI_FALSE);
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::Skirt,                    { (unsigned char) colors[0],  (unsigned char) colors[1],  (unsigned char) colors[2] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::ExternalPerimeter,        { (unsigned char) colors[3],  (unsigned char) colors[4],  (unsigned char) colors[5] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterial,          { (unsigned char) colors[6],  (unsigned char) colors[7],  (unsigned char) colors[8] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SupportMaterialInterface, { (unsigned char) colors[9],  (unsigned char) colors[10], (unsigned char) colors[11] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::InternalInfill,           { (unsigned char) colors[12], (unsigned char) colors[13], (unsigned char) colors[14] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::SolidInfill,              { (unsigned char) colors[15], (unsigned char) colors[16], (unsigned char) colors[17] });
        ref->viewer.set_extrusion_role_color(libvgcode::EGCodeExtrusionRole::WipeTower,                { (unsigned char) colors[18], (unsigned char) colors[19], (unsigned char) colors[20] });
        env->ReleaseIntArrayElements(colorsArr, colors, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1render(JNIEnv* env, jclass, jlong ptr, jfloatArray viewMatrixArr, jfloatArray projectionMatrixArr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jfloat* viewMatrix = env->GetFloatArrayElements(viewMatrixArr, JNI_FALSE);
        jfloat* projectionMatrix = env->GetFloatArrayElements(projectionMatrixArr, JNI_FALSE);

        libvgcode::Mat4x4 converted_view_matrix;
        std::memcpy(converted_view_matrix.data(), viewMatrix, 16 * sizeof(float));
        libvgcode::Mat4x4 converted_projection_matrix;
        std::memcpy(converted_projection_matrix.data(), projectionMatrix, 16 * sizeof(float));

        ref->viewer.render(converted_view_matrix, converted_projection_matrix);

        env->ReleaseFloatArrayElements(viewMatrixArr, viewMatrix, JNI_ABORT);
        env->ReleaseFloatArrayElements(projectionMatrixArr, projectionMatrix, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1init(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        if (ref->initialized) return;
        ref->viewer.init(reinterpret_cast<const char*>(glGetString(GL_VERSION)));
        ref->initialized = true;
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1initialized(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->initialized;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1load(JNIEnv* env, jclass, jlong ptr, jlong resultPtr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        GCodeResultRef* resultRef = (GCodeResultRef*) (intptr_t) resultPtr;

        ref->data = libvgcode_convert_input_data(resultRef->result, resultRef->result.extruder_colors, resultRef->result.extruder_colors, ref->viewer);
        ref->viewer.load(std::move(ref->data));
        ref->viewer.set_time_mode(libvgcode::ETimeMode::Normal);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1reset(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.reset();
        ref->initialized = false;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1count(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_layers_count();
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1set_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr, jlong min, jlong max) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.set_layers_view_range(static_cast<uint32_t>(min), static_cast<uint32_t>(max));
    }

    JNIEXPORT jlongArray JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1layers_1view_1range(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        jlongArray arr = env->NewLongArray(2);
        auto range = ref->viewer.get_layers_view_range();
        jlong min = range[0], max = range[1];
        env->SetLongArrayRegion(arr, 0, 1, &min);
        env->SetLongArrayRegion(arr, 0, 2, &max);
        return arr;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1release(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.shutdown();
        delete ref;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time(JNIEnv* env, jclass, jlong ptr) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_estimated_time();
    }

    libvgcode::EGCodeExtrusionRole mapRole(int index) {
        libvgcode::EGCodeExtrusionRole crole;
        switch (index) {
            default:
            case 0:
                crole = libvgcode::EGCodeExtrusionRole::None;
                break;
            case 1:
                crole = libvgcode::EGCodeExtrusionRole::Perimeter;
                break;
            case 2:
                crole = libvgcode::EGCodeExtrusionRole::ExternalPerimeter;
                break;
            case 3:
                crole = libvgcode::EGCodeExtrusionRole::OverhangPerimeter;
                break;
            case 4:
                crole = libvgcode::EGCodeExtrusionRole::InternalInfill;
                break;
            case 5:
                crole = libvgcode::EGCodeExtrusionRole::SolidInfill;
                break;
            case 6:
                crole = libvgcode::EGCodeExtrusionRole::TopSolidInfill;
                break;
            case 7:
                crole = libvgcode::EGCodeExtrusionRole::Ironing;
                break;
            case 8:
                crole = libvgcode::EGCodeExtrusionRole::BridgeInfill;
                break;
            case 9:
                crole = libvgcode::EGCodeExtrusionRole::GapFill;
                break;
            case 10:
                crole = libvgcode::EGCodeExtrusionRole::Skirt;
                break;
            case 11:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterial;
                break;
            case 12:
                crole = libvgcode::EGCodeExtrusionRole::SupportMaterialInterface;
                break;
            case 13:
                crole = libvgcode::EGCodeExtrusionRole::WipeTower;
                break;
            case 14:
                crole = libvgcode::EGCodeExtrusionRole::Custom;
                break;
        }
        return crole;
    }

    JNIEXPORT jfloat JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1get_1estimated_1time_1role(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.get_extrusion_role_estimated_time(mapRole(role));
    }

    JNIEXPORT jboolean JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1is_1extrusion_1role_1visible(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        return ref->viewer.is_extrusion_role_visible(mapRole(role));
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_slicebeam_slic3r_Native_vgcode_1toggle_1extrusion_1role_1visibility(JNIEnv* env, jclass, jlong ptr, jint role) {
        GCodeViewerRef* ref = (GCodeViewerRef*) (intptr_t) ptr;
        ref->viewer.toggle_extrusion_role_visibility(mapRole(role));
    }
}