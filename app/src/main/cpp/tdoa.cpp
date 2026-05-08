#include <jni.h>
#include <cmath>
#include <limits>
#include <vector>

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_haohui_temperature_1and_1humidity_measurement_NativeTdoaEstimator_nativeEstimate(
        JNIEnv *env,
        jobject,
        jshortArray leftArray,
        jshortArray rightArray,
        jint sampleRate,
        jint maxLag) {
    const jsize leftLength = env->GetArrayLength(leftArray);
    const jsize rightLength = env->GetArrayLength(rightArray);
    const jint usableLength = leftLength < rightLength ? leftLength : rightLength;

    jfloat output[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    if (usableLength <= 1 || sampleRate <= 0 || maxLag < 0) {
        jfloatArray result = env->NewFloatArray(4);
        env->SetFloatArrayRegion(result, 0, 4, output);
        return result;
    }

    std::vector<jshort> left(usableLength);
    std::vector<jshort> right(usableLength);
    env->GetShortArrayRegion(leftArray, 0, usableLength, left.data());
    env->GetShortArrayRegion(rightArray, 0, usableLength, right.data());

    const jint boundedMaxLag = maxLag < usableLength - 1 ? maxLag : usableLength - 1;
    double bestCorr = -std::numeric_limits<double>::infinity();
    double bestAbsCorr = 0.0;
    jint bestLag = 0;
    double totalEnergy = 0.0;

    for (jint lag = -boundedMaxLag; lag <= boundedMaxLag; ++lag) {
        double corr = 0.0;
        double energyLeft = 0.0;
        double energyRight = 0.0;
        jint count = 0;

        for (jint i = 0; i < usableLength; ++i) {
            const jint rightIndex = i + lag;
            if (rightIndex < 0 || rightIndex >= usableLength) {
                continue;
            }
            const double l = static_cast<double>(left[i]);
            const double r = static_cast<double>(right[rightIndex]);
            corr += l * r;
            energyLeft += l * l;
            energyRight += r * r;
            ++count;
        }

        if (count == 0 || energyLeft <= 0.0 || energyRight <= 0.0) {
            continue;
        }

        const double normalized = corr / std::sqrt(energyLeft * energyRight);
        if (std::abs(normalized) > bestAbsCorr || normalized > bestCorr) {
            bestCorr = normalized;
            bestAbsCorr = std::abs(normalized);
            bestLag = lag;
        }
        totalEnergy += energyLeft + energyRight;
    }

    if (!std::isfinite(bestCorr) || totalEnergy <= 0.0) {
        jfloatArray result = env->NewFloatArray(4);
        env->SetFloatArrayRegion(result, 0, 4, output);
        return result;
    }

    output[0] = static_cast<jfloat>(bestLag);
    output[1] = static_cast<jfloat>(static_cast<double>(bestLag) / static_cast<double>(sampleRate));
    output[2] = static_cast<jfloat>(bestCorr);
    output[3] = static_cast<jfloat>(bestAbsCorr);

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, output);
    return result;
}
