import SwiftUI

struct LoginView: View {
    @EnvironmentObject var sync: SyncService

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "barcode.viewfinder")
                .font(.system(size: 72))
                .foregroundStyle(.tint)
            Text("قارئ الباركود المتعدد")
                .font(.title).bold()

            if sync.phase == .notAllowed {
                Text("حسابك (\(sync.email)) غير مصرّح له بعد.\nتم إرسال طلب للمشرف للموافقة.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                Button("تسجيل الخروج") { sync.signOut() }
            } else {
                Button {
                    sync.signIn()
                } label: {
                    Label("تسجيل الدخول عبر Google", systemImage: "person.crop.circle")
                        .padding(.horizontal, 8)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }

            if let message = sync.message {
                Text(message).foregroundStyle(.red).font(.caption)
            }
        }
        .padding()
    }
}
