import Foundation
import Security

protocol TokenStoring: Sendable {
    var token: String? { get async }
    func save(_ token: String) async
    func clear() async
}

/// Keychain-backed session token storage, mirroring the Android client's
/// Keystore-backed `TokenStorage` (see `apps/android/.../core/auth/KeystoreTokenStorage.kt`).
actor KeychainTokenStorage: TokenStoring {
    private let service = "io.github.spasarnaudov.trackertavex.token"
    private let account = "session-token"
    private var cached: String?
    private var hasLoaded = false

    var token: String? {
        get async {
            if !hasLoaded { load() }
            return cached
        }
    }

    func save(_ token: String) async {
        cached = token
        hasLoaded = true
        SecItemDelete(query() as CFDictionary)
        var attributes = query()
        attributes[kSecValueData as String] = Data(token.utf8)
        SecItemAdd(attributes as CFDictionary, nil)
    }

    func clear() async {
        cached = nil
        hasLoaded = true
        SecItemDelete(query() as CFDictionary)
    }

    private func load() {
        hasLoaded = true
        var lookupQuery = query()
        lookupQuery[kSecReturnData as String] = true
        lookupQuery[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(lookupQuery as CFDictionary, &result)
        if status == errSecSuccess, let data = result as? Data {
            cached = String(data: data, encoding: .utf8)
        }
    }

    private func query() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}
