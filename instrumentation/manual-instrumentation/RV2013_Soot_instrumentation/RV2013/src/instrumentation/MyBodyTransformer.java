package instrumentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.BooleanType;
import soot.Local;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.EqExpr;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NopStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;

public class MyBodyTransformer extends BodyTransformer{

	@Override
	protected void internalTransform(Body body, String arg0, Map arg1) {
		if (body.getMethod().getDeclaringClass().getName().startsWith("de.ecspride")) {
			Iterator<Unit> i = body.getUnits().snapshotIterator();
			while (i.hasNext()) {
				Unit u = i.next();
				if(u.toString().contains("println")){
	//				removeStatement(u, body);				
	//				replaceStatementByLog(u, body);
				}
				eliminatePremiumRateSMS(u, body);
			}
		}
	}
	
	private void removeStatement(Unit u, Body body){
			body.getUnits().remove(u);
	}
	
	private void replaceStatementByLog(Unit u, Body body){
		SootMethod sm = Scene.v().getMethod("<android.util.Log: int i(java.lang.String,java.lang.String)>");
		Value logType = StringConstant.v("INFO");
		Value logMessage = StringConstant.v("replaced log information");
		
		StaticInvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(sm.makeRef(), logType, logMessage);
		Unit generated = Jimple.v().newInvokeStmt(invokeExpr);
		
		body.getUnits().insertAfter(generated, u);
		
		body.getUnits().remove(u);
	}
	
	private void eliminatePremiumRateSMS(Unit u, Body body){		
		if(u instanceof InvokeStmt){
			InvokeStmt invoke = (InvokeStmt)u;
			
			if(invoke.getInvokeExpr().getMethod().getSignature().equals("<android.telephony.SmsManager: void sendTextMessage(java.lang.String,java.lang.String,java.lang.String,android.app.PendingIntent,android.app.PendingIntent)>")){
				Value phoneNumber = invoke.getInvokeExpr().getArg(0);
				
				if(phoneNumber instanceof StringConstant){
					removeStatement(u, body);
				}
				else if(body.getLocals().contains(phoneNumber)){
					Local phoneNumberLocal = (Local)phoneNumber;
					List<Unit> generated = new ArrayList<Unit>();
					
					//generate startsWith method
					VirtualInvokeExpr vinvokeExpr = generateStartsWithMethod(body, phoneNumberLocal);
					
					//generate assignment of local (boolean) with the startsWith method
					Type booleanType = BooleanType.v();
					Local localBoolean = generateNewLocal(body, booleanType);
					AssignStmt astmt = Jimple.v().newAssignStmt(localBoolean, vinvokeExpr);
					generated.add(astmt);
					
					//generate condition
					IntConstant zero = IntConstant.v(0);
					EqExpr equalExpr = Jimple.v().newEqExpr(localBoolean, zero);
					NopStmt nop = insertNopStmt(body, u);
					IfStmt ifStmt = Jimple.v().newIfStmt(equalExpr, nop);
					generated.add(ifStmt);
					
					body.getUnits().insertBefore(generated, u);
				}
			}
				
		}
	}
	
	private VirtualInvokeExpr generateStartsWithMethod(Body body, Local phoneNumberLocal){
		SootMethod sm = Scene.v().getMethod("<java.lang.String: boolean startsWith(java.lang.String)>");
		
		
		Value value = StringConstant.v("0900");
		VirtualInvokeExpr vinvokeExpr = Jimple.v().newVirtualInvokeExpr(phoneNumberLocal, sm.makeRef(), value);
		return vinvokeExpr;
	}
	
	private Local generateNewLocal(Body body, Type type){
		LocalGenerator lg = new LocalGenerator(body);
		return lg.generateLocal(type);
	}	
	
	private NopStmt insertNopStmt(Body body, Unit u){
		NopStmt nop = Jimple.v().newNopStmt();
		body.getUnits().insertAfter(nop, u);
		return nop;
	}
}
