import Foundation

/// Type-erasing wrapper so `APIClient` can accept any request body without
/// relying on `Encodable` conforming to itself (it doesn't).
struct AnyEncodable: Encodable {
    private let encodeClosure: (Encoder) throws -> Void

    init<T: Encodable>(_ wrapped: T) {
        encodeClosure = wrapped.encode
    }

    func encode(to encoder: Encoder) throws {
        try encodeClosure(encoder)
    }
}
