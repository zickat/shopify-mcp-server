package com.zickat.shopifymcpserver.shared_kernel

import org.bson.types.ObjectId

fun String.toObjectIdOrNull(): ObjectId? = if (ObjectId.isValid(this)) ObjectId(this) else null

fun newObjectIdHex(): String = ObjectId().toHexString()
