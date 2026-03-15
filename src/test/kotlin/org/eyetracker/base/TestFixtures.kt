package org.eyetracker.base

object TestFixtures {
    const val VALID_LOGIN = "user@test.com"
    const val VALID_PASSWORD = "SecureP@ss123"
    const val ADMIN_LOGIN = "admin@test.com"
    const val SUPER_ADMIN_LOGIN = "superadmin@test.com"

    // Minimal valid 1x1 PNG (67 bytes)
    val sampleCoverBytes: ByteArray = byteArrayOf(
        -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
        0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, -112, 119, 83,
        -34, 0, 0, 0, 12, 73, 68, 65, 84, 8, -41, 99, -8, -49, -64, 0,
        0, 0, 3, 0, 1, 24, -40, 95, -89, 0, 0, 0, 0, 73, 69, 78,
        68, -82, 66, 96, -126
    )

    // Minimal valid JPEG (smallest valid JFIF)
    val sampleImageBytes: ByteArray = byteArrayOf(
        -1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1,
        0, 1, 0, 0, -1, -37, 0, 67, 0, 8, 6, 6, 7, 6, 5, 8,
        7, 7, 7, 9, 9, 8, 10, 12, 20, 13, 12, 11, 11, 12, 25, 18,
        19, 15, 20, 29, 26, 31, 30, 29, 26, 28, 28, 32, 36, 46, 39, 32,
        34, 44, 35, 28, 28, 40, 55, 41, 44, 48, 49, 52, 52, 52, 31, 39,
        57, 61, 56, 50, 60, 46, 51, 52, 50, -1, -55, 0, 11, 8, 0, 1,
        0, 1, 1, 1, 17, 0, -1, -60, 0, 31, 0, 0, 1, 5, 1, 1,
        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4,
        5, 6, 7, 8, 9, 10, 11, -1, -60, 0, -75, 16, 0, 2, 1, 3,
        3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 125, 1, 2, 3, 0,
        -1, -38, 0, 8, 1, 1, 0, 0, 63, 0, 125, -1, -39
    )

    fun authHeader(token: String) = "Bearer $token"
}
