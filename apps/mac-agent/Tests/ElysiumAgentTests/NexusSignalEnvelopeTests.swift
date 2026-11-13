import Foundation
import XCTest
@testable import elysium_agent

final class NexusSignalEnvelopeTests: XCTestCase {
    private let sessionID = UUID(uuidString: "123e4567-e89b-42d3-a456-426614174000")!
    private let pairingSecret = Data((0..<32).map(UInt8.init))
    private let body = Data("{\"sdp\":\"test\"}".utf8)

    func testSigningMatchesAndroidVectorAndRoundTrips() throws {
        let envelope = try NexusSignalEnvelope.signWithNonceForTesting(
            pairingSecret: pairingSecret,
            sessionID: sessionID,
            sender: .android,
            sequence: 7,
            sentAtMillis: 1_700_000_000_000,
            kind: .offer,
            body: body,
            nonce: Data((0..<12).map(UInt8.init))
        )

        XCTAssertEqual(envelope.macBase64URL, "D6GTvNzb6CgitNuWYHvHYY5Ppp1UckyMbZkbp5l6r3I")
        XCTAssertEqual(envelope.bodyBase64URL, "AAECAwQFBgcICQoLm68j8PJCWlW1tGFR69kzJPbHqmasyNfdjFOhIfZG")

        let decoded = try JSONDecoder().decode(NexusSignalEnvelope.self, from: envelope.encodedJSON())
        XCTAssertEqual(
            decoded.verifiedBody(
                pairingSecret: pairingSecret,
                expectedSessionID: sessionID,
                expectedSender: .android,
                nowMillis: 1_700_000_000_000
            ),
            body
        )
    }

    func testRejectsTamperingWrongPeerWrongSessionExpiredSignalAndWrongSecret() throws {
        let envelope = try NexusSignalEnvelope.sign(
            pairingSecret: pairingSecret,
            sessionID: sessionID,
            sender: .mac,
            sequence: 1,
            sentAtMillis: 1_700_000_000_000,
            kind: .answer,
            body: body
        )

        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: envelope.encodedJSON()) as? [String: Any])
        json["body"] = "eyJzZHAiOiJib2ciZX0"
        let tampered = try JSONDecoder().decode(
            NexusSignalEnvelope.self,
            from: JSONSerialization.data(withJSONObject: json)
        )
        XCTAssertNil(tampered.verifiedBody(
            pairingSecret: pairingSecret,
            expectedSessionID: sessionID,
            expectedSender: .mac,
            nowMillis: 1_700_000_000_000
        ))

        XCTAssertNil(envelope.verifiedBody(
            pairingSecret: pairingSecret,
            expectedSessionID: sessionID,
            expectedSender: .android,
            nowMillis: 1_700_000_000_000
        ))
        XCTAssertNil(envelope.verifiedBody(
            pairingSecret: pairingSecret,
            expectedSessionID: UUID(uuidString: "123e4567-e89b-42d3-a456-426614174001")!,
            expectedSender: .mac,
            nowMillis: 1_700_000_000_000
        ))
        XCTAssertNil(envelope.verifiedBody(
            pairingSecret: pairingSecret,
            expectedSessionID: sessionID,
            expectedSender: .mac,
            nowMillis: 1_700_000_121_000
        ))
        XCTAssertNil(envelope.verifiedBody(
            pairingSecret: Data(repeating: 0, count: 31),
            expectedSessionID: sessionID,
            expectedSender: .mac,
            nowMillis: 1_700_000_000_000
        ))
    }

    func testReplayWindowAcceptsReorderingButRejectsDuplicatesAndOldSignals() {
        var replayWindow = NexusSignalReplayWindow(width: 4)
        XCTAssertTrue(replayWindow.accept(10))
        XCTAssertTrue(replayWindow.accept(12))
        XCTAssertTrue(replayWindow.accept(11))
        XCTAssertFalse(replayWindow.accept(11))
        XCTAssertTrue(replayWindow.accept(16))
        XCTAssertFalse(replayWindow.accept(12))
        XCTAssertFalse(replayWindow.accept(0))
    }
}
