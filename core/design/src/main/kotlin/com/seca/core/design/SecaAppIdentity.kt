package com.seca.core.design

/**
 * Which Seca app is being themed.
 *
 * All three share tokens, shapes, typography and motion; only the accent
 * differs, so the family reads as one system while each app stays
 * recognisable at a glance.
 */
enum class SecaAppIdentity(val packageName: String) {
    Contacts("com.seca.contacts"),
    Phone("com.seca.phone"),
    Messages("com.seca.messages"),
}
