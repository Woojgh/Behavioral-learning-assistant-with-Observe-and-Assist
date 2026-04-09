package com.example.aiassistant

data class NodeSnapshot(
    val text: String?,
    val contentDesc: String?,
    val className: String?,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean
)

data class ScreenSnapshot(
    val packageName: String,
    val stableState: String,
    val nodes: List<NodeSnapshot>,
    val textElements: List<String>
)
