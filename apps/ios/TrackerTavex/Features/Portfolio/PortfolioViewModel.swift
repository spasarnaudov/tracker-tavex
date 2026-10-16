import Foundation

struct HoldingEdit: Identifiable {
    let assetId: Int
    let symbol: String
    let name: String
    var quantityText: String
    var includeInChart: Bool
    var id: Int { assetId }
}

struct ManualItemEdit: Identifiable {
    let id = UUID()
    var serverId: Int?
    var name: String
    var quantityText: String
    var unitPriceText: String
    var priceAssetId: Int?
    var includeInChart: Bool
}

/// Editable holdings + manual items, mirroring the Android client's inline-edit
/// Portfolio screen (quantity field + include-in-chart checkbox per row — see
/// apps/android/README.md → "Edit holding" is inline"). PUT /portfolio always sends
/// every holding, not just changed ones, and reloads local state from the response
/// body directly rather than issuing a follow-up GET (apps/android/docs/API_INTEGRATION.md).
@MainActor
final class PortfolioViewModel: ObservableObject {
    @Published var holdingEdits: [HoldingEdit] = []
    @Published var manualItemEdits: [ManualItemEdit] = []
    @Published private(set) var goldBuybackAssets: [GoldBuybackAsset] = []
    @Published private(set) var totalValue: Double = 0
    @Published private(set) var isLoading = false
    @Published private(set) var isSaving = false
    @Published var errorMessage: String?
    @Published var saveMessage: String?

    private var deletedManualItemIds: [Int] = []
    private let session: SessionStore

    init(session: SessionStore) {
        self.session = session
    }

    func load() async {
        isLoading = true
        errorMessage = nil
        saveMessage = nil
        defer { isLoading = false }
        do {
            let response = try await session.apiClient.portfolio()
            applyResponse(response)
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addManualItem() {
        manualItemEdits.append(
            ManualItemEdit(serverId: nil, name: "", quantityText: "", unitPriceText: "", priceAssetId: nil, includeInChart: false)
        )
    }

    func removeManualItem(_ item: ManualItemEdit) {
        if let serverId = item.serverId {
            deletedManualItemIds.append(serverId)
        }
        manualItemEdits.removeAll { $0.id == item.id }
    }

    func save() async {
        errorMessage = nil
        saveMessage = nil

        var holdingRequests: [UpdateHoldingRequest] = []
        for edit in holdingEdits {
            guard let quantity = Double(edit.quantityText), quantity >= 0 else {
                errorMessage = "\(edit.symbol): quantity must be a number that isn't negative."
                return
            }
            holdingRequests.append(
                UpdateHoldingRequest(assetId: edit.assetId, quantity: quantity, includeInChart: edit.includeInChart)
            )
        }

        var manualRequests: [UpdateManualItemRequest] = []
        for edit in manualItemEdits {
            let name = edit.name.trimmingCharacters(in: .whitespaces)
            guard !name.isEmpty else {
                errorMessage = "Manual item name cannot be blank."
                return
            }
            guard let quantity = Double(edit.quantityText), quantity >= 0 else {
                errorMessage = "\(name): quantity must be a number that isn't negative."
                return
            }
            let unitPrice: Double
            if edit.priceAssetId == nil {
                guard let parsed = Double(edit.unitPriceText), parsed >= 0 else {
                    errorMessage = "\(name): unit price must be a number that isn't negative."
                    return
                }
                unitPrice = parsed
            } else {
                unitPrice = 0
            }
            manualRequests.append(
                UpdateManualItemRequest(
                    id: edit.serverId,
                    name: name,
                    quantity: quantity,
                    unitPrice: unitPrice,
                    priceAssetId: edit.priceAssetId,
                    includeInChart: edit.includeInChart,
                    delete: false
                )
            )
        }
        for deletedId in deletedManualItemIds {
            manualRequests.append(
                UpdateManualItemRequest(
                    id: deletedId,
                    name: "",
                    quantity: 0,
                    unitPrice: 0,
                    priceAssetId: nil,
                    includeInChart: false,
                    delete: true
                )
            )
        }

        isSaving = true
        defer { isSaving = false }
        do {
            let response = try await session.apiClient.updatePortfolio(
                UpdatePortfolioRequest(holdings: holdingRequests, manualItems: manualRequests)
            )
            applyResponse(response)
            saveMessage = "Saved."
        } catch let error as AppError {
            errorMessage = error.message
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func applyResponse(_ response: PortfolioResponse) {
        holdingEdits = response.holdings.map {
            HoldingEdit(
                assetId: $0.assetId,
                symbol: $0.symbol,
                name: $0.name,
                quantityText: Self.formatNumber($0.quantity),
                includeInChart: $0.includeInChart
            )
        }
        manualItemEdits = response.manualItems.map {
            ManualItemEdit(
                serverId: $0.id,
                name: $0.name,
                quantityText: Self.formatNumber($0.quantity),
                unitPriceText: Self.formatNumber($0.unitPrice),
                priceAssetId: $0.priceAssetId,
                includeInChart: $0.includeInChart
            )
        }
        goldBuybackAssets = response.goldBuybackAssets
        totalValue = response.totalValue
        deletedManualItemIds = []
    }

    private static func formatNumber(_ value: Double) -> String {
        value == value.rounded() && abs(value) < 1e15 ? String(Int(value)) : String(value)
    }
}
