import fs from "fs"
import path from "path"

const helper = fs.readFileSync(path.join(__dirname, "..", "..", "..", "tools", "open-dashboard.ps1"), "utf8").replace(/\r\n/g, "\n")

describe("the dashboard helper's safety lines", () => {
    it("forwards only on the endpoint it connected to", () => {
        expect(helper).toContain('& $adb -s $Endpoint forward "tcp:$Port" "tcp:$Port"')
    })

    it("accepts only a host:port endpoint", () => {
        expect(helper).toContain("$Endpoint -notmatch '^[A-Za-z0-9._-]+:[0-9]{1,5}$'")
    })

    it("range-checks the port", () => {
        expect(helper).toContain("$Port -lt 1 -or $Port -gt 65535")
    })
})
