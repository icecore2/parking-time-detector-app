package com.parktimedetector.service

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Abstraction over Android's AccessibilityNodeInfo to enable both
 * production AccessibilityService interaction and fast, deterministic unit test validation.
 */
interface AccessibilityNode {
    val text: CharSequence?
    val contentDescription: CharSequence?
    val className: CharSequence?
    val viewIdResourceName: String?
    val isClickable: Boolean
    val isEditable: Boolean
    val childCount: Int
    fun getChild(index: Int): AccessibilityNode?
    val parent: AccessibilityNode?
    fun performAction(action: Int, arguments: Bundle? = null): Boolean
}

class RealAccessibilityNode(private val node: AccessibilityNodeInfo) : AccessibilityNode {
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val className: CharSequence? get() = node.className
    override val viewIdResourceName: String? get() = node.viewIdResourceName
    override val isClickable: Boolean get() = node.isClickable
    override val isEditable: Boolean get() = node.isEditable
    override val childCount: Int get() = node.childCount

    override fun getChild(index: Int): AccessibilityNode? {
        val child = node.getChild(index) ?: return null
        return RealAccessibilityNode(child)
    }

    override val parent: AccessibilityNode?
        get() = node.parent?.let { RealAccessibilityNode(it) }

    override fun performAction(action: Int, arguments: Bundle?): Boolean {
        return if (arguments != null) {
            node.performAction(action, arguments)
        } else {
            node.performAction(action)
        }
    }
}
