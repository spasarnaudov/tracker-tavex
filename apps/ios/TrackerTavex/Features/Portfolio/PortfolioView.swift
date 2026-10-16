import SwiftUI

struct PortfolioView: View {
    @ObservedObject var session: SessionStore
    @StateObject private var viewModel: PortfolioViewModel

    init(session: SessionStore) {
        self.session = session
        _viewModel = StateObject(wrappedValue: PortfolioViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        Text("Total value")
                            .font(MaterialFont.titleMedium)
                            .foregroundStyle(MaterialColor.onSurface)
                        Spacer()
                        Text(viewModel.totalValue, format: .number.precision(.fractionLength(2)))
                            .font(MaterialFont.titleMedium)
                            .foregroundStyle(MaterialColor.primary)
                    }
                }

                Section {
                    ForEach($viewModel.holdingEdits) { $holding in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("\(holding.name) (\(holding.symbol))")
                                .font(MaterialFont.titleSmall)
                                .foregroundStyle(MaterialColor.onSurface)
                            TextField("Quantity", text: $holding.quantityText)
                                .keyboardType(.decimalPad)
                                .textFieldStyle(.materialOutlined)
                            Toggle("Include in chart", isOn: $holding.includeInChart)
                                .font(MaterialFont.bodyMedium)
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("Holdings").font(MaterialFont.labelLarge)
                }

                Section {
                    ForEach($viewModel.manualItemEdits) { $item in
                        VStack(alignment: .leading, spacing: 6) {
                            TextField("Name", text: $item.name)
                                .textFieldStyle(.materialOutlined)
                            TextField("Quantity", text: $item.quantityText)
                                .keyboardType(.decimalPad)
                                .textFieldStyle(.materialOutlined)
                            Picker("Price source", selection: $item.priceAssetId) {
                                Text("Manual price").tag(Int?.none)
                                ForEach(viewModel.goldBuybackAssets) { asset in
                                    Text(asset.name).tag(Optional(asset.id))
                                }
                            }
                            if item.priceAssetId == nil {
                                TextField("Unit price", text: $item.unitPriceText)
                                    .keyboardType(.decimalPad)
                                    .textFieldStyle(.materialOutlined)
                            }
                            Toggle("Include in chart", isOn: $item.includeInChart)
                                .font(MaterialFont.bodyMedium)
                        }
                        .padding(.vertical, 4)
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                viewModel.removeManualItem(item)
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text("Manual items").font(MaterialFont.labelLarge)
                        Spacer()
                        Button("Add") { viewModel.addManualItem() }
                            .buttonStyle(.materialTonal)
                    }
                }

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(MaterialColor.error)
                    }
                }

                if let saveMessage = viewModel.saveMessage {
                    Section {
                        Text(saveMessage)
                            .foregroundStyle(MaterialColor.primary)
                    }
                }
            }
            .materialList()
            .navigationTitle("Portfolio")
            .refreshable { await viewModel.load() }
            .overlay {
                if viewModel.isLoading && viewModel.holdingEdits.isEmpty {
                    ProgressView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await viewModel.save() }
                    } label: {
                        if viewModel.isSaving {
                            ProgressView()
                        } else {
                            Text("Save")
                        }
                    }
                    .disabled(viewModel.isSaving)
                }
            }
            .task { await viewModel.load() }
        }
    }
}
