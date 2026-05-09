#include <jni.h>
#include <cmath>
#include <algorithm>
#include <limits>
#include <vector>

namespace {
constexpr double kHighPassCutoffHz = 500.0;
constexpr double kMinNormalizationPeak = 1e-6;
constexpr jint kMinOverlapSamples = 100;
constexpr double kMinCorrelationPeak = 0.7;

std::vector<double> highPassNormalize(const std::vector<jshort> &input, jint sampleRate) {
    std::vector<double> output(input.size());
    const double alpha = 1.0 / (1.0 + 2.0 * M_PI * kHighPassCutoffHz / static_cast<double>(sampleRate));
    double previousInput = 0.0;
    double peak = 0.0;

    for (size_t i = 0; i < input.size(); ++i) {
        const double currentInput = static_cast<double>(input[i]);
        const double filtered = alpha * (currentInput - previousInput);
        output[i] = filtered;
        peak = std::max(peak, std::abs(filtered));
        previousInput = currentInput;
    }

    if (peak > kMinNormalizationPeak) {
        for (double &sample : output) {
            sample /= peak;
        }
    }
    return output;
}
}

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
    const std::vector<double> processedLeft = highPassNormalize(left, sampleRate);
    const std::vector<double> processedRight = highPassNormalize(right, sampleRate);
    double bestCorr = -std::numeric_limits<double>::infinity();
    jint bestLag = 0;

    for (jint lag = -boundedMaxLag; lag <= boundedMaxLag; ++lag) {
        const jint startLeft = lag < 0 ? -lag : 0;
        const jint startRight = lag > 0 ? lag : 0;
        const jint validLength = usableLength - std::abs(lag);
        if (validLength < kMinOverlapSamples) {
            continue;
        }

        double meanLeft = 0.0;
        double meanRight = 0.0;
        for (jint i = 0; i < validLength; ++i) {
            meanLeft += processedLeft[startLeft + i];
            meanRight += processedRight[startRight + i];
        }
        meanLeft /= static_cast<double>(validLength);
        meanRight /= static_cast<double>(validLength);

        double cross = 0.0;
        double leftVariance = 0.0;
        double rightVariance = 0.0;
        for (jint i = 0; i < validLength; ++i) {
            const double l = processedLeft[startLeft + i] - meanLeft;
            const double r = processedRight[startRight + i] - meanRight;
            cross += l * r;
            leftVariance += l * l;
            rightVariance += r * r;
        }

        if (leftVariance <= 0.0 || rightVariance <= 0.0) {
            continue;
        }

        const double normalized = cross / std::sqrt(leftVariance * rightVariance);
        if (normalized > bestCorr) {
            bestCorr = normalized;
            bestLag = lag;
        }
    }

    if (!std::isfinite(bestCorr) || bestCorr < kMinCorrelationPeak) {
        jfloatArray result = env->NewFloatArray(4);
        env->SetFloatArrayRegion(result, 0, 4, output);
        return result;
    }

    output[0] = static_cast<jfloat>(bestLag);
    output[1] = static_cast<jfloat>(static_cast<double>(bestLag) / static_cast<double>(sampleRate));
    output[2] = static_cast<jfloat>(bestCorr);
    output[3] = static_cast<jfloat>(bestCorr);

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, output);
    return result;
}
