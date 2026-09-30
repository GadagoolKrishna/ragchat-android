# Security Policy: RagChat Android SDK

## 1. Supported Versions

We provide security patches and vulnerability updates for the current major release and the active Long-Term Support (LTS) release branch:

| Version | Supported | Notes |
| :--- | :--- | :--- |
| **1.0.x** | ✅ Yes | Current Stable Release (LTS) |
| **< 1.0.0** | ❌ No | Alpha / Beta releases (unsupported) |

---

## 2. Reporting a Vulnerability

The RagChat team takes security vulnerabilities seriously. If you discover a potential vulnerability in the RagChat Android SDK, please report it privately:

- **Email**: Send detailed disclosure reports to `security@ragchat.dev`.
- **GPG Key**: Encrypt disclosures using our team PGP key (Fingerprint: `B84C 73D2 99E1 28F4 110A 5B7E C4A9 0081 D1E8 442A`).
- **Response SLA**:
  - **Initial Acknowledgment**: Within 24 hours.
  - **Triage & Severity Assessment**: Within 48 hours.
  - **Remediation & Patch Release**: Within 7 business days for Critical/High severity.

### Responsible Disclosure Guidelines
- **Do not** disclose vulnerabilities publicly or file public GitHub issues before an official patch has been published.
- Provide step-by-step reproduction instructions or a minimal test case demonstrating the vulnerability.
- Include sanitized logs; **never include real PII, credentials, or proprietary documents** in your report.

---

## 3. Bug Bounty Program
Critical vulnerabilities in cryptographic storage, prompt injection bypasses, or data isolation boundaries may be eligible for recognition in our security hall of fame and bounty awards.
