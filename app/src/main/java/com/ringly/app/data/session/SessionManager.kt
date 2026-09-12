package com.ringly.app.data.session

import com.ringly.app.data.repository.UserRepository

class SessionManager(
    private val storage: UserSessionStorage,
    private val userRepository: UserRepository = UserRepository()
) {

    val userId: String?
        get() = storage.userId

    val userName: String?
        get() = storage.userName

    val isRegistered: Boolean
        get() = userId != null

    suspend fun ensureRegistered(deviceId: String, name: String): Result<String> {
        userId?.let { return Result.success(it) }

        return userRepository.register(deviceId, name)
            .onSuccess { response ->
                storage.userId = response.userId
                storage.userName = name
            }
            .map { it.userId }
    }

    fun clear() {
        storage.clear()
    }
}