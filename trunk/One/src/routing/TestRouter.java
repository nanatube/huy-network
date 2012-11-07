package routing;

import core.Settings;

public class TestRouter extends ActiveRouter {

	public TestRouter(Settings s) {
		super(s);
		// TODO Auto-generated constructor stub
	}

	public TestRouter(TestRouter testRouter) {
		super(testRouter);
	}

	@Override
	public TestRouter replicate() {
		return new TestRouter(this);
	}
}
