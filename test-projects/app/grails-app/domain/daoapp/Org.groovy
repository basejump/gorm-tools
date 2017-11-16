package daoapp

class Org {
	String name
	String num
    Address address
    Date testDate
    boolean isActive=true
    BigDecimal revenue = 0
    Long refId = 0L

    static quickSearchFields = ["name", "num"]
    static constraints = {
		name blank:false
		num nullable: true
        address nullable: true
        testDate nullable: true
    }
}
