import Foundation

/// Talks to the Tracker Tavex `/api/v1` REST API (`apps/flask/api.py`).
/// The base URL is read fresh on every call via `baseURLProvider` so a change in
/// Connection Settings takes effect on the next request without rebuilding this client.
final class APIClient {
    private let baseURLProvider: @Sendable () -> String
    private let tokenStorage: any TokenStoring

    init(baseURLProvider: @escaping @Sendable () -> String, tokenStorage: any TokenStoring) {
        self.baseURLProvider = baseURLProvider
        self.tokenStorage = tokenStorage
    }

    private var decoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }

    private var encoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }

    func health() async throws -> HealthResponse {
        try await requestJSON("health", method: "GET")
    }

    func login(username: String, password: String, force: Bool) async throws -> AuthResponse {
        try await requestJSON(
            "auth/login",
            method: "POST",
            body: AnyEncodable(LoginRequest(username: username, password: password, force: force ? true : nil))
        )
    }

    func session() async throws -> SessionResponse {
        try await requestJSON("auth/session", method: "GET", authorized: true)
    }

    func logout() async throws {
        _ = try await send("auth/logout", method: "POST", authorized: true)
    }

    func portfolio() async throws -> PortfolioResponse {
        try await requestJSON("portfolio", method: "GET", authorized: true)
    }

    func updatePortfolio(_ update: UpdatePortfolioRequest) async throws -> PortfolioResponse {
        try await requestJSON("portfolio", method: "PUT", body: AnyEncodable(update), authorized: true)
    }

    func register(username: String, password: String) async throws -> AuthResponse {
        try await requestJSON(
            "auth/register",
            method: "POST",
            body: AnyEncodable(RegisterRequest(username: username, password: password))
        )
    }

    func changePassword(currentPassword: String, newPassword: String) async throws -> MessageResponse {
        try await requestJSON(
            "account/password",
            method: "PUT",
            body: AnyEncodable(ChangePasswordRequest(currentPassword: currentPassword, newPassword: newPassword)),
            authorized: true
        )
    }

    func deleteAccount() async throws {
        _ = try await send("account", method: "DELETE", authorized: true)
    }

    func assets() async throws -> AssetsResponse {
        try await requestJSON("assets", method: "GET", authorized: true)
    }

    func assetPrices(assetId: Int, range: String, interval: String) async throws -> AssetPricesResponse {
        try await requestJSON(
            "assets/\(assetId)/prices?\(Self.query(["range": range, "interval": interval]))",
            method: "GET",
            authorized: true
        )
    }

    func portfolioHistory(range: String, interval: String) async throws -> PortfolioHistoryResponse {
        try await requestJSON(
            "portfolio/history?\(Self.query(["range": range, "interval": interval]))",
            method: "GET",
            authorized: true
        )
    }

    func adminUsers() async throws -> [AdminUser] {
        try await requestJSON("admin/users", method: "GET", authorized: true)
    }

    func adminLoginHistory() async throws -> [AdminLoginHistoryEntry] {
        try await requestJSON("admin/login-history", method: "GET", authorized: true)
    }

    func adminLogFiles() async throws -> [AdminLogFile] {
        try await requestJSON("admin/logs", method: "GET", authorized: true)
    }

    func adminLogContent(name: String) async throws -> String {
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        let data = try await send("admin/logs/\(encodedName)", method: "GET", authorized: true)
        return String(data: data, encoding: .utf8) ?? ""
    }

    private static func query(_ parameters: [String: String]) -> String {
        parameters
            .map { key, value in
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
                return "\(key)=\(encodedValue)"
            }
            .joined(separator: "&")
    }

    private func requestJSON<T: Decodable>(
        _ path: String,
        method: String,
        body: AnyEncodable? = nil,
        authorized: Bool = false
    ) async throws -> T {
        let data = try await send(path, method: method, body: body, authorized: authorized)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw AppError.unknown("Could not read the server response.")
        }
    }

    private func send(
        _ path: String,
        method: String,
        body: AnyEncodable? = nil,
        authorized: Bool = false
    ) async throws -> Data {
        guard let url = URL(string: baseURLProvider() + path) else {
            throw AppError.unknown("The server address is not valid.")
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
            urlRequest.httpBody = try encoder.encode(body)
        }
        if authorized, let token = await tokenStorage.token {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: urlRequest)
        } catch {
            throw AppError.network("Could not reach the server. Check your connection and try again.")
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AppError.unknown("No response from the server.")
        }

        if (200...299).contains(httpResponse.statusCode) {
            return data
        }

        if httpResponse.statusCode == 401, authorized {
            await tokenStorage.clear()
        }

        let errorBody = try? decoder.decode(ApiErrorBody.self, from: data)
        let message = errorBody?.error.message ?? "Request failed with status \(httpResponse.statusCode)."
        let details = errorBody?.error.details ?? []

        switch httpResponse.statusCode {
        case 400: throw AppError.badRequest(message)
        case 401: throw AppError.unauthorized(message)
        case 403: throw AppError.forbidden(message)
        case 404: throw AppError.notFound(message)
        case 409 where errorBody?.error.code == "active_session": throw AppError.activeSessionConflict(message)
        case 409: throw AppError.conflict(message)
        case 422: throw AppError.validationFailed(message, details: details)
        case 500...599: throw AppError.serverError(message)
        default: throw AppError.unknown(message)
        }
    }
}
