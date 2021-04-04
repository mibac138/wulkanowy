package io.github.wulkanowy.utils

import io.github.wulkanowy.data.db.entities.Student

inline val Student.nickOrName get() = if (nick.isBlank()) studentName else nick
