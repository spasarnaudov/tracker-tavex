import SwiftUI
import Charts

struct AssetDetailView: View {
    let asset: Asset
    @StateObject private var viewModel: AssetDetailViewModel

    init(session: SessionStore, asset: Asset) {
        self.asset = asset
        _viewModel = StateObject(wrappedValue: AssetDetailViewModel(session: session, assetId: asset.id))
    }

    var body: some View {
        List {
            Section {
                Picker("Range", selection: $viewModel.range) {
                    ForEach(AssetDetailViewModel.ranges, id: \.self) { Text($0) }
                }
                .pickerStyle(.segmented)

                Picker("Interval", selection: $viewModel.interval) {
                    ForEach(AssetDetailViewModel.intervals, id: \.self) { Text($0) }
                }
            }

            Section {
                if viewModel.prices.isEmpty {
                    if viewModel.isLoading {
                        ProgressView()
                    } else {
                        Text("No price data for this range.")
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Chart(viewModel.prices) { point in
                        if let price = point.price, let date = DateParsing.parse(point.priceDate) {
                            LineMark(x: .value("Date", date), y: .value("Price", price))
                                .foregroundStyle(MaterialColor.primary)
                        }
                    }
                    .frame(height: 240)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(MaterialColor.error)
                }
            }
        }
        .materialList()
        .navigationTitle(asset.name)
        .onChange(of: viewModel.range) { _, _ in Task { await viewModel.load() } }
        .onChange(of: viewModel.interval) { _, _ in Task { await viewModel.load() } }
        .task { await viewModel.load() }
    }
}
