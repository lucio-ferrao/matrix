package io.element.android.libraries.matrix.ui.contacts

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.getDirectRoomMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * MatriX: private names for contacts, kept in the account data of the user, so only they see them, on all their devices.
 * The content is a JSON object from user id to name.
 */
@SingleIn(SessionScope::class)
@Inject
class ContactNicknames(
    private val client: MatrixClient,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) {
    private val nicknames = MutableStateFlow<Map<UserId, String>>(emptyMap())

    /** All the names given, by user id. */
    val all: StateFlow<Map<UserId, String>> = nicknames.asStateFlow()

    init {
        sessionCoroutineScope.launch {
            client.getAccountData(EVENT_TYPE).getOrNull()?.let { nicknames.value = parse(it) }
        }
    }

    /** The name given to [userId], if any. */
    fun nicknameFlow(userId: UserId): Flow<String?> = nicknames.map { it[userId] }.distinctUntilChanged()

    /** The name of [room]: in a room with one other person the name given to them, if any, else the name of the room. */
    fun roomNameFlow(room: BaseRoom): Flow<String?> = combine(room.roomInfoFlow, room.membersStateFlow, nicknames) { info, members, names ->
        val otherMember = if (info.isDm) members.getDirectRoomMember(info, room.sessionId) else null
        otherMember?.let { names[it.userId] } ?: info.name
    }.distinctUntilChanged()

    /** Gives [nickname] to [userId], or removes their name when it is blank. */
    suspend fun setNickname(userId: UserId, nickname: String?): Result<Unit> {
        val updated = nicknames.value.toMutableMap().apply {
            if (nickname.isNullOrBlank()) remove(userId) else put(userId, nickname.trim())
        }
        val content = JSONObject(updated.mapKeys { it.key.value }).toString()
        return client.setAccountData(EVENT_TYPE, content).onSuccess { nicknames.value = updated }
    }

    companion object {
        /** The type of the account data event with the names. */
        const val EVENT_TYPE = "org.ferrao.matrix.nicknames"

        /** The names in the [content] of the account data event. */
        fun parse(content: String): Map<UserId, String> {
            val json = JSONObject(content)
            return json.keys().asSequence().associate { UserId(it) to json.getString(it) }
        }
    }
}
