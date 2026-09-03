package com.sabri.intrudercatcher

/**
 * IntruderCatcher
 * المطور: Engineer/Sabri
 *
 * هذا الملف هو "قلب" التطبيق. نظام أندرويد يستدعي onPasswordFailed()
 * تلقائيًا في كل مرة يُدخل فيها المستخدم PIN أو نمط (Pattern) خاطئ
 * على شاشة القفل — بشرط أن يكون تطبيقنا مفعّلاً كـ Device Admin.
 *
 * ملاحظة أمنية مهمة (لا يمكن تجاوزها بأي كود):
 * نظام أندرويد لا يرسل أي إشعار لأي تطبيق خارجي عند فشل البصمة
 * أو فشل التعرف على الوجه (Biometric). هذا قرار أمني متعمّد من Google
 * لحماية بيانات المستخدمين البيومترية من أي تطبيق طرف ثالث، ولا توجد
 * صلاحية أو API عام يكسر هذا القيد. لذلك هذا الـ Receiver يغطي فقط
 * حالتي PIN والنمط، وهما الحالتان الوحيدتان اللتان يفضح عنهما أندرويد.
 */

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LockAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "تم تفعيل صلاحية مدير الجهاز")
        AppLockPrefs.setAdminWasDisabled(context, false)
    }

    // V15: يُستدعى لحظة ما المستخدم يحاول تعطيل الصلاحية من الإعدادات - أندرويد
    // يعرض النص اللي نرجّعه هنا داخل مربع التأكيد الرسمي قبل السماح بالتعطيل.
    // هذا أقصى ما يسمح به النظام؛ لا توجد طريقة برمجية لمنع التعطيل نهائيًا أو
    // طلب PIN داخل هذا المربع نفسه دون صلاحية إضافية كبيرة (Accessibility Service).
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "تم تعطيل صلاحية مدير الجهاز")

        // نسجّل الحدث ونلتقط صورة فورية لمن عطّل الصلاحية، ونرفع علامة
        // تنبيه قوي تظهر في MainActivity في أول فرصة يُفتح فيها التطبيق
        AttemptLogManager.add(
            context, context.packageName, context.getString(R.string.app_name),
            AttemptLogManager.METHOD_ADMIN_DISABLED, false
        )
        AppLockPrefs.setAdminWasDisabled(context, true)
        CaptureService.enqueueCapture(context)
    }

    // هذه الدالة يستدعيها نظام أندرويد تلقائيًا عند إدخال PIN أو نمط خاطئ
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "تم رصد محاولة فتح فاشلة - جارِ محاولة التقاط صورة")

        // نستدعي خدمة الالتقاط بالخلفية (بدون فتح أي واجهة أو معاينة)
        CaptureService.enqueueCapture(context)
    }

    companion object {
        private const val TAG = "LockAdminReceiver"
    }
}
