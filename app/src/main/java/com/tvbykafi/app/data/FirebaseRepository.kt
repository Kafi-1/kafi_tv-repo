package com.tvbykafi.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tvbykafi.app.data.model.AppConfig
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.data.model.PaymentRequest
import com.tvbykafi.app.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FirebaseRepository private constructor() {

    companion object {
        @Volatile
        private var instance: FirebaseRepository? = null

        fun getInstance(): FirebaseRepository {
            return instance ?: synchronized(this) {
                instance ?: FirebaseRepository().also { instance = it }
            }
        }
    }

    private val db = FirebaseFirestore.getInstance()
    private val usersCol = db.collection("users")
    private val settingsDoc = db.document("settings/app_config")
    private val paymentCol = db.collection("payment_requests")

    private val playlistRepo = PlaylistRepository.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // =================== CHANNELS (M3U playlist) ===================

    fun fetchChannels(
        onSuccess: (List<Channel>) -> Unit,
        onError: () -> Unit = {}
    ) {
        scope.launch {
            try {
                onSuccess(playlistRepo.fetchPlaylist())
            } catch (_: Exception) {
                onError()
            }
        }
    }

    fun fetchCategories(
        onSuccess: (List<String>) -> Unit,
        onError: () -> Unit = {}
    ) {
        scope.launch {
            try {
                val channels = playlistRepo.fetchPlaylist()
                onSuccess(channels.map { it.category }.distinct().sorted())
            } catch (_: Exception) {
                onError()
            }
        }
    }

    fun clearCache() {
        playlistRepo.clearCache()
    }

    // =================== AUTH ===================

    fun login(
        email: String,
        pin: String,
        deviceId: String,
        onSuccess: (docId: String, user: User) -> Unit,
        onDeviceLimit: () -> Unit,
        onError: () -> Unit
    ) {
        usersCol
            .whereEqualTo("email", email)
            .whereEqualTo("pin", pin)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onError()
                    return@addOnSuccessListener
                }
                val doc = snap.documents[0]
                val userData = doc.data ?: run { onError(); return@addOnSuccessListener }

                @Suppress("UNCHECKED_CAST")
                val deviceIds = (userData["deviceIds"] as? List<String>) ?: emptyList()
                val limit = (userData["device_limit"] as? Long)?.toInt() ?: 2

                if (!deviceIds.contains(deviceId)) {
                    if (deviceIds.size >= limit) {
                        onDeviceLimit()
                        return@addOnSuccessListener
                    }
                    doc.reference.update("deviceIds", FieldValue.arrayUnion(deviceId))
                }

                val user = User(
                    id = doc.id,
                    name = userData["name"] as? String ?: "",
                    email = userData["email"] as? String ?: "",
                    pin = userData["pin"] as? String ?: "",
                    phone = userData["phone"] as? String ?: "",
                    expiry = userData["expiry"] as? String ?: "",
                    start_at = userData["start_at"] as? String ?: "",
                    role = userData["role"] as? String ?: "user",
                    device_limit = limit,
                    deviceIds = deviceIds,
                    favorites = (userData["favorites"] as? List<String>) ?: emptyList()
                )
                onSuccess(doc.id, user)
            }
            .addOnFailureListener { onError() }
    }

    // =================== USER REALTIME ===================

    fun observeUser(
        userId: String,
        onUpdate: (User) -> Unit,
        onDeleted: () -> Unit
    ): ListenerRegistration {
        return usersCol.document(userId).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) {
                onDeleted()
                return@addSnapshotListener
            }
            val d = snap.data ?: return@addSnapshotListener
            @Suppress("UNCHECKED_CAST")
            val user = User(
                id = snap.id,
                name = d["name"] as? String ?: "",
                email = d["email"] as? String ?: "",
                pin = d["pin"] as? String ?: "",
                phone = d["phone"] as? String ?: "",
                expiry = d["expiry"] as? String ?: "",
                start_at = d["start_at"] as? String ?: "",
                role = d["role"] as? String ?: "user",
                device_limit = (d["device_limit"] as? Long)?.toInt() ?: 2,
                deviceIds = (d["deviceIds"] as? List<String>) ?: emptyList(),
                favorites = (d["favorites"] as? List<String>) ?: emptyList()
            )
            onUpdate(user)
        }
    }

    // =================== APP SETTINGS REALTIME ===================

    fun observeAppConfig(onUpdate: (AppConfig) -> Unit): ListenerRegistration {
        return settingsDoc.addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val d = snap.data ?: return@addSnapshotListener
            onUpdate(AppConfig(
                monthly_price = d["monthly_price"] as? String ?: "",
                expire_message = d["expire_message"] as? String ?: "",
                support_whatsapp = d["support_whatsapp"] as? String ?: "",
                support_telegram = d["support_telegram"] as? String ?: "",
                support_email = d["support_email"] as? String ?: "",
                bkash_num = d["bkash_num"] as? String ?: "",
                nagad_num = d["nagad_num"] as? String ?: ""
            ))
        }
    }

    // =================== FAVORITES ===================

    fun addFavorite(userId: String, channelId: String) {
        usersCol.document(userId).update("favorites", FieldValue.arrayUnion(channelId))
    }

    fun removeFavorite(userId: String, channelId: String) {
        usersCol.document(userId).update("favorites", FieldValue.arrayRemove(channelId))
    }

    // =================== PAYMENT ===================

    fun observeUserPayments(userId: String, onUpdate: (List<PaymentRequest>) -> Unit): ListenerRegistration {
        return paymentCol.whereEqualTo("userId", userId)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    PaymentRequest(
                        id = doc.id,
                        userId = d["userId"] as? String ?: "",
                        userName = d["userName"] as? String ?: "",
                        number = d["number"] as? String ?: "",
                        trxId = d["trxId"] as? String ?: "",
                        status = d["status"] as? String ?: "Pending",
                        timestamp = d["timestamp"] as? String ?: ""
                    )
                }.sortedByDescending { it.timestamp }
                onUpdate(list)
            }
    }

    fun submitPayment(
        userId: String,
        userName: String,
        number: String,
        trxId: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val data = hashMapOf(
            "userId" to userId,
            "userName" to userName,
            "number" to number,
            "trxId" to trxId,
            "status" to "Pending",
            "timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
        )
        paymentCol.add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError() }
    }
}
