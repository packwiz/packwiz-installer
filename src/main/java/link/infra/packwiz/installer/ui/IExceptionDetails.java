package link.infra.packwiz.installer.ui;

public interface IExceptionDetails {
	Exception getException();
	String getName();

	enum ExceptionListResult {
		CONTINUE, CANCEL, IGNORE
	}
}
