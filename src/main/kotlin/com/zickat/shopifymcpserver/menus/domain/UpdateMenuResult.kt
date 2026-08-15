package com.zickat.shopifymcpserver.menus.domain

enum class UpdateMenuOutcome { UPDATED, NO_FIELD_PROVIDED, HANDLE_CHANGE_NOT_CONFIRMED, FAILED }

data class UpdateMenuResult(
    val outcome: UpdateMenuOutcome,
    val menuId: String? = null,
    val titleChange: Pair<String, String>? = null,
    val handleChange: Pair<String, String>? = null,
    val itemCount: Int? = null,
    val requestedHandle: String? = null,
    val warnings: List<String> = emptyList(),
    val failureDetail: String? = null,
) {
    companion object {
        fun updated(
            menuId: String,
            titleChange: Pair<String, String>?,
            handleChange: Pair<String, String>?,
            itemCount: Int,
            warnings: List<String>,
        ) = UpdateMenuResult(UpdateMenuOutcome.UPDATED, menuId, titleChange, handleChange, itemCount, warnings = warnings)

        fun noFieldProvided() = UpdateMenuResult(UpdateMenuOutcome.NO_FIELD_PROVIDED)

        fun handleChangeNotConfirmed(handle: String) = UpdateMenuResult(UpdateMenuOutcome.HANDLE_CHANGE_NOT_CONFIRMED, requestedHandle = handle)

        fun failed(detail: String) = UpdateMenuResult(UpdateMenuOutcome.FAILED, failureDetail = detail)
    }
}
