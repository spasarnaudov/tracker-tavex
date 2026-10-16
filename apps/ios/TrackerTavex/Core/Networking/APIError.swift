import Foundation

/// Mirrors the `error.code` / HTTP status conventions used by `apps/flask/api.py`
/// (see `apps/android/docs/API_INTEGRATION.md` for the Android side of this mapping).
enum AppError: Error, Equatable {
    case badRequest(String)
    case unauthorized(String)
    case forbidden(String)
    case notFound(String)
    case activeSessionConflict(String)
    case conflict(String)
    case validationFailed(String, details: [String])
    case serverError(String)
    case network(String)
    case unknown(String)

    var message: String {
        switch self {
        case .badRequest(let message),
             .unauthorized(let message),
             .forbidden(let message),
             .notFound(let message),
             .activeSessionConflict(let message),
             .conflict(let message),
             .serverError(let message),
             .network(let message),
             .unknown(let message):
            return message
        case .validationFailed(let message, _):
            return message
        }
    }
}

struct ApiErrorBody: Decodable {
    struct ErrorDetail: Decodable {
        let code: String
        let message: String
        let details: [String]?
    }

    let error: ErrorDetail
}
