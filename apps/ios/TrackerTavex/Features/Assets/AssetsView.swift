import SwiftUI

struct AssetsView: View {
    @ObservedObject var session: SessionStore
    @StateObject private var viewModel: AssetsViewModel

    init(session: SessionStore) {
        self.session = session
        _viewModel = StateObject(wrappedValue: AssetsViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    PortfolioHistoryView(session: session)
                } header: {
                    Text("Portfolio value").font(MaterialFont.labelLarge)
                }

                if !viewModel.goldBuybackAssets.isEmpty {
                    Section {
                        ForEach(viewModel.goldBuybackAssets) { asset in
                            HStack {
                                Text(asset.name)
                                    .font(MaterialFont.bodyLarge)
                                    .foregroundStyle(MaterialColor.onSurface)
                                Spacer()
                                if let price = asset.price {
                                    Text(price, format: .number.precision(.fractionLength(2)))
                                        .font(MaterialFont.bodyMedium)
                                        .foregroundStyle(MaterialColor.onSurfaceVariant)
                                }
                            }
                        }
                    } header: {
                        Text("Gold buyback").font(MaterialFont.labelLarge)
                    }
                }

                Section {
                    ForEach(viewModel.assets) { asset in
                        NavigationLink(value: asset) {
                            VStack(alignment: .leading) {
                                Text(asset.name)
                                    .font(MaterialFont.bodyLarge)
                                    .foregroundStyle(MaterialColor.onSurface)
                                Text(asset.symbol)
                                    .font(MaterialFont.bodyMedium)
                                    .foregroundStyle(MaterialColor.onSurfaceVariant)
                            }
                        }
                    }
                } header: {
                    Text("Assets").font(MaterialFont.labelLarge)
                }

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(MaterialColor.error)
                    }
                }
            }
            .materialList()
            .navigationTitle("Assets")
            .navigationDestination(for: Asset.self) { asset in
                AssetDetailView(session: session, asset: asset)
            }
            .refreshable { await viewModel.load() }
            .overlay {
                if viewModel.isLoading && viewModel.assets.isEmpty {
                    ProgressView()
                }
            }
            .task { await viewModel.load() }
        }
    }
}
